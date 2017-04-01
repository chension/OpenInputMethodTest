package com.chen.openinputmethodtest.service;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.chen.openinputmethodtest.keyboard.CandidateViewListener;
import com.chen.openinputmethodtest.keyboard.CandidatesContainer;
import com.chen.openinputmethodtest.keyboard.ComposingView;
import com.chen.openinputmethodtest.keyboard.DecodingInfo;
import com.chen.openinputmethodtest.keyboard.IPinyinDecoderService;
import com.chen.openinputmethodtest.keyboard.InputModeSwitcher;
import com.chen.openinputmethodtest.keyboard.PinyinDecoderService;
import com.chen.openinputmethodtest.keyboard.SkbContainer;
import com.chen.openinputmethodtest.keyboard.SoftKey;
import com.chen.openinputmethodtest.utils.MeasureHelper;
import com.chen.openinputmethodtest.utils.OPENLOG;
import com.open.inputmethod.R;

/**
 * 输入法服务.
 *
 * @author hailong.qiu 356752238@qq.com
 */
public class IMEService extends InputMethodService {

    private static final String TAG = "IMEService";

    InputModeSwitcher mInputModeSwitcher;
    SkbContainer      mSkbContainer;
    EditorInfo        mSaveEditorInfo;

    /**
     * 候选词视图集装箱
     */
    public CandidatesContainer mCandidatesContainer;

    /**
     * 词库解码操作对象
     */
    public DecodingInfo mDecInfo = new DecodingInfo();
    /**
     * 链接
     * 词库解码远程服务PinyinDecoderService 的监听器
     */
    private PinyinDecoderServiceConnection mPinyinDecoderServiceConnection;
    /**
     * 当前的输入法状态
     */
    public static ImeState mImeState = ImeState.STATE_IDLE;
    /**
     * 当用户选择了候选词或者在候选词视图滑动了手势时的通知输入法。 实现了候选词视图的监听器CandidateViewListener。
     */
    private ChoiceNotifier mChoiceNotifier;
    private LinearLayout   mFloatingContainer;
    public ComposingView  mComposingView;
    private PopupWindow    mFloatingWindow;
    private PopupTimer mFloatingWindowTimer = new PopupTimer();

    @Override
    public void onCreate() {
        super.onCreate();
        OPENLOG.D(TAG, "onCreate");

        // 绑定词库解码远程服务PinyinDecoderService
        startPinyinDecoderService();
        // 读取屏幕的宽高.
        MeasureHelper measureHelper = MeasureHelper.getInstance();
        measureHelper.onConfigurationChanged(getResources().getConfiguration(), this);
        //
        mInputModeSwitcher = new InputModeSwitcher();
        mChoiceNotifier = new ChoiceNotifier(this);
    }

    @Override
    public View onCreateInputView() {
        OPENLOG.D(TAG, "onCreateInputView");
        LayoutInflater inflater = getLayoutInflater();
        mSkbContainer = (SkbContainer) inflater.inflate(R.layout.skb_container, null);
        mSkbContainer.setService(this);
        mSkbContainer.setInputModeSwitcher(mInputModeSwitcher);
        return mSkbContainer;
    }

    @Override
    public View onCreateCandidatesView() {
        OPENLOG.D(TAG, "onCreateCandidatesView");
        LayoutInflater inflater = getLayoutInflater();

        // 设置显示输入拼音字符串View的集装箱
        mFloatingContainer = (LinearLayout) inflater.inflate(
                R.layout.floating_container, null);

        // The first child is the composing view.
        mComposingView = (ComposingView) mFloatingContainer.getChildAt(0);

        // 设置候选词集装箱
        mCandidatesContainer = (CandidatesContainer) inflater.inflate(
                R.layout.candidates_container, null);

        mCandidatesContainer.initialize(mChoiceNotifier);

        // todo:这个
        if (null != mFloatingWindow && mFloatingWindow.isShowing()) {
            mFloatingWindowTimer.cancelShowing();
            mFloatingWindow.dismiss();
        }
        mFloatingWindow = new PopupWindow(this);
        mFloatingWindow.setClippingEnabled(false);
        mFloatingWindow.setBackgroundDrawable(null);
        mFloatingWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mFloatingWindow.setContentView(mFloatingContainer);

        setCandidatesViewShown(true);
        return mCandidatesContainer;
    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        OPENLOG.D(TAG, "onStartInput");
        mSaveEditorInfo = editorInfo;
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        OPENLOG.D(TAG, "onStartInputView");
        // 根据inputType设置软键盘样式.
        mInputModeSwitcher.setInputMode(editorInfo);
        mSkbContainer.updateInputMode(null);
    }

    @Override
    public void onDestroy() {
        OPENLOG.D(TAG, "onDestroy");

        // 解绑定词库解码远程服务PinyinDecoderService
        unbindService(mPinyinDecoderServiceConnection);
        super.onDestroy();
    }

    /**
     * 1. onConfigurationChanged事件并不是只有屏幕方向改变才可以触发，<br>
     * 其他的一些系统设置改变也可以触发，比如打开或者隐藏键盘。<br>
     * 2. 屏幕方向发生改变时 <br>
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        OPENLOG.D(TAG, "onConfigurationChanged newConfig:" + newConfig);
        MeasureHelper measureHelper = MeasureHelper.getInstance();
        measureHelper.onConfigurationChanged(newConfig, this);
        super.onConfigurationChanged(newConfig);

        //重置到空闲状态
        resetToIdleState(false);
    }

    /**
     * 重置到空闲状态
     *
     * @param resetInlineText
     */
    public void resetToIdleState(boolean resetInlineText) {
        if (ImeState.STATE_IDLE == mImeState)
            return;

        mImeState = ImeState.STATE_IDLE;
        mDecInfo.reset();

        // 重置显示输入拼音字符串的 View
        if (null != mComposingView)
            mComposingView.reset();
        if (resetInlineText)
            commitResultText("");

        resetCandidateWindow();
    }

    /**
     * 重置候选词区域
     */
    private void resetCandidateWindow() {
        if (null == mCandidatesContainer)
            return;
        try {
            mFloatingWindowTimer.cancelShowing();
            mFloatingWindow.dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Fail to show the PopupWindow.");
        }

        if (null != mSkbContainer && mSkbContainer.isShown()) {
            mSkbContainer.toggleCandidateMode(false);
        }

        mDecInfo.resetCandidates();

        if (null != mCandidatesContainer && mCandidatesContainer.isShown()) {
            showCandidateWindow(false);
        }
    }

    /**
     * 显示候选词视图
     *
     * @param showComposingView 是否显示输入的拼音View
     */
    public void showCandidateWindow(boolean showComposingView) {
        Log.d(TAG, "Candidates window is shown. Parent = "
                + mCandidatesContainer);

        setCandidatesViewShown(true);

        if (null != mSkbContainer)
            mSkbContainer.requestLayout();

        if (null == mCandidatesContainer) {
            resetToIdleState(false);
            return;
        }

        updateComposingText(showComposingView);
        mCandidatesContainer.showCandidates(mDecInfo,
                ImeState.STATE_COMPOSING != mImeState);
        mFloatingWindowTimer.postShowFloatingWindow();
    }

    /**
     * 设置是否显示输入拼音的view
     *
     * @param visible
     */
    public void updateComposingText(boolean visible) {
        if (!visible) {
            mComposingView.setVisibility(View.INVISIBLE);
        } else {
            mComposingView.setDecodingInfo(mDecInfo, mImeState);
            mComposingView.setVisibility(View.VISIBLE);
        }
        mComposingView.invalidate();
    }

    /**
     * 显示输入的拼音字符串PopupWindow 定时器
     *
     * @author keanbin
     * @ClassName PopupTimer
     */
    private class PopupTimer extends Handler implements Runnable {
        private int mParentLocation[] = new int[2];

        void postShowFloatingWindow() {
            mFloatingContainer.measure(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mFloatingWindow.setWidth(mFloatingContainer.getMeasuredWidth());
            mFloatingWindow.setHeight(mFloatingContainer.getMeasuredHeight());
            post(this);
        }

        void cancelShowing() {
            if (mFloatingWindow.isShowing()) {
                mFloatingWindow.dismiss();
            }
            removeCallbacks(this);
        }

        public void run() {
            // 获取候选集装箱的位置
            mCandidatesContainer.getLocationInWindow(mParentLocation);

            if (!mFloatingWindow.isShowing()) {
                // 显示候选词PopupWindow
                mFloatingWindow.showAtLocation(mCandidatesContainer,
                        Gravity.LEFT | Gravity.TOP, mParentLocation[0],
                        mParentLocation[1] - mFloatingWindow.getHeight());
            } else {
                // 更新候选词PopupWindow
                mFloatingWindow
                        .update(mParentLocation[0], mParentLocation[1]
                                        - mFloatingWindow.getHeight(),
                                mFloatingWindow.getWidth(),
                                mFloatingWindow.getHeight());
            }
        }
    }

    /**
     * 发送字符到编辑框(EditText)
     */
    public void commitResultText(String resultText) {
        OPENLOG.D(TAG, "commitResultText resultText:" + resultText);
        InputConnection ic = getCurrentInputConnection();
        if (null != ic && !TextUtils.isEmpty(resultText)) {
            ic.commitText(resultText, 1);
        }
    }

    public static final int MAX_INT = Integer.MAX_VALUE / 2 - 1;

    public void setCursorRightMove() {
        int cursorPos = getSelectionStart();
        cursorPos++;
        getCurrentInputConnection().setSelection(cursorPos, cursorPos);
    }

    public void setCursorLeftMove() {
        int cursorPos = getSelectionStart();
        cursorPos -= 1;
        if (cursorPos < 0)
            cursorPos = 0;
        getCurrentInputConnection().setSelection(cursorPos, cursorPos);
    }

    private int getSelectionStart() {
        return getCurrentInputConnection().getTextBeforeCursor(MAX_INT, 0).length();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 防止输入法退出还在监听事件.
        OPENLOG.D(TAG, "onKeyDown keyCode:" + keyCode+":::"+(mDecInfo.mIPinyinDecoderService==null));
        if (isImeServiceStop()) {
            OPENLOG.D(TAG, "onKeyDown isImeServiceStop keyCode:" + keyCode);
            return super.onKeyDown(keyCode, event);
        }

        //在这里对softkeyboardview和candidateview的焦点进行控制
        OPENLOG.D(TAG,":::"+(null != mCandidatesContainer && mCandidatesContainer.isShown()
                && !mDecInfo.isCandidatesListEmpty()));
        if (mSkbContainer.isCanProcess()){
            return mSkbContainer != null && mSkbContainer.onSoftKeyDown(keyCode, event);
        }else if (mCandidatesContainer.isCanProcess()){
            return processCandidateKey(keyCode);
        }else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private boolean processCandidateKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // 选择当前高亮的候选词
            chooseCandidate(-1);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // 高亮位置向上一个候选词移动或者移动到上一页的最后一个候选词的位置。
            mCandidatesContainer.activeCurseBackward();
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // 高亮位置向下一个候选词移动或者移动到下一页的第一个候选词的位置。
            mCandidatesContainer.activeCurseForward();
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            // 到上一页候选词
            mCandidatesContainer.pageBackward(false, true);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // 到下一页候选词
//            mCandidatesContainer.pageForward(false, true);
            mCandidatesContainer.setCanProcess(false);
            mSkbContainer.setCanProcess(true);
            mCandidatesContainer.enableActiveHighlight(false);
            SoftKey nSelectSoftKey = mSkbContainer.mSoftKeyboardView.getSoftKeyboard().getSelectSoftKey();
            nSelectSoftKey.setKeySelected(true);
        }

        // 在预报状态下的删除键处理
        if (keyCode == KeyEvent.KEYCODE_DEL
                && ImeState.STATE_PREDICT == mImeState) {
            resetToIdleState(false);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // 防止输入法退出还在监听事件.
        if (isImeServiceStop()) {
            OPENLOG.D(TAG, "onKeyUp isImeServiceStop keyCode:" + keyCode);
            return super.onKeyDown(keyCode, event);
        }
        OPENLOG.D(TAG, "onKeyUp keyCode:" + keyCode);
        return mSkbContainer != null && mSkbContainer.onSoftKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    /**
     * 防止输入法退出还在监听事件.
     */
    public boolean isImeServiceStop() {
        return ((mSkbContainer == null) || !isInputViewShown());
    }

    /**
     * 防止全屏.
     */
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    /**
     * 绑定词库解码远程服务PinyinDecoderService
     */
    private boolean startPinyinDecoderService() {
        if (null == mDecInfo.mIPinyinDecoderService) {
            OPENLOG.D(TAG,"startPinyinDecoderService");
            Intent serviceIntent = new Intent();
            serviceIntent.setClass(this, PinyinDecoderService.class);

            if (null == mPinyinDecoderServiceConnection) {
                OPENLOG.D(TAG,"mPinyinDecoderServiceConnection");
                mPinyinDecoderServiceConnection = new PinyinDecoderServiceConnection();
            }

            // Bind service
            return bindService(serviceIntent, mPinyinDecoderServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }
        return true;
    }

    /**
     * Connection used for binding to the Pinyin decoding service.
     * 词库解码远程服务PinyinDecoderService 的监听器
     */
    public class PinyinDecoderServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            OPENLOG.D(TAG,"onServiceConnected");
            mDecInfo.mIPinyinDecoderService = IPinyinDecoderService.Stub
                    .asInterface(service);
        }

        public void onServiceDisconnected(ComponentName name) {
        }
    }

    /**
     * 当用户选择了候选词或者在候选词视图滑动了手势时的通知输入法。实现了候选词视图的监听器CandidateViewListener，
     * 有选择候选词的处理函数、手势向右滑动的处理函数、手势向左滑动的处理函数 、手势向上滑动的处理函数、手势向下滑动的处理函数。
     */
    private class ChoiceNotifier extends Handler implements
            CandidateViewListener {
        IMEService mIme;

        ChoiceNotifier(IMEService ime) {
            mIme = ime;
        }

        public void onClickChoice(int choiceId) {
            if (choiceId >= 0) {
                mIme.onChoiceTouched(choiceId);
            }
        }

        public void onToLeftGesture() {
            if (ImeState.STATE_COMPOSING == mImeState) {
                changeToStateInput(true);
            }
            mCandidatesContainer.pageForward(true, false);
        }

        public void onToRightGesture() {
            if (ImeState.STATE_COMPOSING == mImeState) {
                changeToStateInput(true);
            }
            mCandidatesContainer.pageBackward(true, false);
        }

        public void onToTopGesture() {
        }

        public void onToBottomGesture() {
        }
    }

    /**
     * 输入法状态
     */
    public enum ImeState {
        STATE_BYPASS, STATE_IDLE, STATE_INPUT, STATE_COMPOSING, STATE_PREDICT, STATE_APP_COMPLETION
    }

    /**
     * 选择候选词后的处理函数。在ChoiceNotifier中实现CandidateViewListener监听器的onClickChoice（）中调用
     */
    private void onChoiceTouched(int activeCandNo) {
        if (mImeState == ImeState.STATE_COMPOSING) {
            changeToStateInput(true);
        } else if (mImeState == ImeState.STATE_INPUT
                || mImeState == ImeState.STATE_PREDICT) {
            // 选择候选词
//            chooseCandidate(activeCandNo);
        } else if (mImeState == ImeState.STATE_APP_COMPLETION) {
            if (null != mDecInfo.mAppCompletions && activeCandNo >= 0
                    && activeCandNo < mDecInfo.mAppCompletions.length) {
                CompletionInfo ci = mDecInfo.mAppCompletions[activeCandNo];
                if (null != ci) {
                    InputConnection ic = getCurrentInputConnection();
                    // 发送从APP中获取的候选词给EditText
                    ic.commitCompletion(ci);
                }
            }
//            resetToIdleState(false);
        }
    }

    /**
     * 设置输入法状态为 mImeState = ImeState.STATE_INPUT;
     *
     * @param updateUi 是否更新UI
     */
    public void changeToStateInput(boolean updateUi) {
        mImeState = ImeState.STATE_INPUT;
        if (!updateUi)
            return;

        if (null != mSkbContainer && mSkbContainer.isShown()) {
            mSkbContainer.toggleCandidateMode(true);
        }
        showCandidateWindow(true);
    }

    /**
     * 设置输入法状态为 mImeState = ImeState.STATE_COMPOSING;
     *
     * @param updateUi
     *            是否更新UI
     */
    public void changeToStateComposing(boolean updateUi) {
        mImeState = ImeState.STATE_COMPOSING;
        if (!updateUi)
            return;

        if (null != mSkbContainer && mSkbContainer.isShown()) {
            mSkbContainer.toggleCandidateMode(true);
        }
    }

    /**
     * 选择候选词，并根据条件是否进行下一步的预报。
     *
     * @param candId 如果candId小于0 ，就对输入的拼音进行查询。
     */
    public void chooseAndUpdate(int candId) {

        // 不是中文输入法状态
        if (!mInputModeSwitcher.isChineseText()) {
            String choice = mDecInfo.getCandidate(candId);
            if (null != choice) {
                commitResultText(choice);
            }
            resetToIdleState(false);
            return;
        }

        if (ImeState.STATE_PREDICT != mImeState) {
            // Get result candidate list, if choice_id < 0, do a new decoding.
            // If choice_id >=0, select the candidate, and get the new candidate
            // list.
            mDecInfo.chooseDecodingCandidate(candId);
        } else {
            // Choose a prediction item.
            mDecInfo.choosePredictChoice(candId);
        }

        if (mDecInfo.getComposingStr().length() > 0) {
            String resultStr;
            // 获取选择了的候选词
            resultStr = mDecInfo.getComposingStrActivePart();

            // choiceId >= 0 means user finishes a choice selection.
            if (candId >= 0 && mDecInfo.canDoPrediction()) {
                // 发生选择了的候选词给EditText
                commitResultText(resultStr);
                // 设置输入法状态为预报
                mImeState = ImeState.STATE_PREDICT;
                // TODO 这一步是做什么？
                if (null != mSkbContainer && mSkbContainer.isShown()) {
                    mSkbContainer.toggleCandidateMode(false);
                }

                // Try to get the prediction list.
                // 获取预报的候选词列表

                InputConnection ic = getCurrentInputConnection();
                if (null != ic) {
                    CharSequence cs = ic.getTextBeforeCursor(3, 0);
                    if (null != cs) {
                        mDecInfo.preparePredicts(cs);
                    }
                }

                if (mDecInfo.mCandidatesList.size() > 0) {
                    showCandidateWindow(false);
                } else {
                    resetToIdleState(false);
                }
            } else {
                if (ImeState.STATE_IDLE == mImeState) {
                    if (mDecInfo.getSplStrDecodedLen() == 0) {
                        changeToStateComposing(true);
                    } else {
                        changeToStateInput(true);
                    }
                } else {
                    if (mDecInfo.selectionFinished()) {
                        changeToStateComposing(true);
                    }
                }
                showCandidateWindow(true);
            }
        } else {
            resetToIdleState(false);
        }
    }

    /**
     * 发送 '\uff0c' 或者 '\u3002' 给EditText
     *
     * @param preEdit
     * @param keyChar
     * @param dismissCandWindow
     *            是否重置候选词窗口
     * @param nextState
     *            mImeState的下一个状态
     */
    public void inputCommaPeriod(String preEdit, int keyChar,
                                  boolean dismissCandWindow, ImeState nextState) {
        if (keyChar == ',')
            preEdit += '\uff0c';
        else if (keyChar == '.')
            preEdit += '\u3002';
        else
            return;
        commitResultText(preEdit);
        if (dismissCandWindow)
            resetCandidateWindow();
        mImeState = nextState;
    }

    /**
     * 添加输入的拼音，然后进行词库查询，或者删除输入的拼音指定的字符或字符串，然后进行词库查询。
     *
     * @param keyChar
     * @param keyCode
     * @return
     */
    public boolean processSurfaceChange(int keyChar, int keyCode) {
        if (mDecInfo.isSplStrFull() && KeyEvent.KEYCODE_DEL != keyCode) {
            return true;
        }

        if ((keyChar >= 'a' && keyChar <= 'z')
                || (keyChar == '\'' && !mDecInfo.charBeforeCursorIsSeparator())
                || (((keyChar >= '0' && keyChar <= '9') || keyChar == ' ') && ImeState.STATE_COMPOSING == mImeState)) {
            mDecInfo.addSplChar((char) keyChar, false);
            chooseAndUpdate(-1);
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            mDecInfo.prepareDeleteBeforeCursor();
            chooseAndUpdate(-1);
        }
        return true;
    }

    /**
     * 选择候选词
     *
     * @param activeCandNo
     *            如果小于0，就选择当前高亮的候选词。
     */
    public void chooseCandidate(int activeCandNo) {
        if (activeCandNo < 0) {
            activeCandNo = mCandidatesContainer.getActiveCandiatePos();
        }
        if (activeCandNo >= 0) {
            chooseAndUpdate(activeCandNo);
        }
    }
}
