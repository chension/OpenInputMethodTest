package com.chen.openinputmethodtest.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.RelativeLayout;

import com.chen.openinputmethodtest.service.IMEService;
import com.chen.openinputmethodtest.utils.MeasureHelper;
import com.chen.openinputmethodtest.utils.OPENLOG;
import com.open.inputmethod.R;

import static com.chen.openinputmethodtest.service.IMEService.mImeState;

/**
 * 软键盘主容器.
 *
 * @author hailong.qiu 356752238@qq.com
 */
public class SkbContainer extends RelativeLayout {

    private static final String TAG = "SkbContainer";

    public SkbContainer(Context context) {
        super(context);
        init(context, null);
    }

    public SkbContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SkbContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private InputModeSwitcher mInputModeSwitcher;
    private SoftKeyboardView  mSoftKeyboardView; // 主要的子软键盘.
    private SoftKeyboardView  mPopupKeyboardView; // 弹出的软键盘.
    private int               mSkbLayout;
    private Context           mContext;
    private IMEService        mService;
    private boolean           mLastCandidatesShowing;


    /**
     * 初始化.
     */
    private void init(Context context, AttributeSet attrs) {
        this.mContext = context;
        View.inflate(context, R.layout.softkey_layout_view, this);
    }

    public void setInputModeSwitcher(InputModeSwitcher imSwitch) {
        mInputModeSwitcher = imSwitch;
    }

    /**
     */
    public void setService(IMEService service) {
        mService = service;
    }

    /**
     * 更新软键盘类型.
     */
    public void updateSoftKeyboardType() {

    }

    public void updateInputMode(SoftKey softKey) {
        int skbLayout = mInputModeSwitcher.getSkbLayout(); // 输入类型转换出布局XML id.
        // 重新加载布局(前提是不能喝前一个布局一样)
        if (mSkbLayout != skbLayout) {
            mSkbLayout = skbLayout;
            updateSkbLayout(); // 更新软键盘布局.
            requestLayout(); // 重新加载软键盘高度.
            setDefualtSelectKey(0, 0); // 设置默认选中的按键.
        }

        //
        if (mSoftKeyboardView != null) {
            SoftKeyboard skb = mSoftKeyboardView.getSoftKeyboard();
            if (null == skb)
                return;
            // 初始化状态.
            skb.enableToggleStates(mInputModeSwitcher.getToggleStates());
            /**
             * 清空原先的键盘缓存.<br>
             * 比如 回车改变了状态.(onstartinputView触发)<br>
             */
            mSoftKeyboardView.clearCacheBitmap();
        }
    }

    /**
     * 切换候选词模式。逻辑简介：先从mInputModeSwitcher输入法模式交换器中获得中文候选词模式状态，然后判断是否是要切入候选词模式，
     * 如果是键盘就变为中文候选词模式状态
     * ，如果不是，键盘就消除中文候选词模式状态，变为mInputModeSwitcher中的mToggleStates设置键盘的状态。
     *
     * @param candidatesShowing
     */
    public void toggleCandidateMode(boolean candidatesShowing) {
        if (null == mSoftKeyboardView || !mInputModeSwitcher.isChineseText()
                || mLastCandidatesShowing == candidatesShowing)
            return;
        mLastCandidatesShowing = candidatesShowing;

        SoftKeyboard skb = mSoftKeyboardView.getSoftKeyboard();
        if (null == skb)
            return;

        int state = 1;//暂定中文输入法的state为1
        if (!candidatesShowing) {
            skb.disableToggleState(state, false);
            skb.enableToggleStates(mInputModeSwitcher.getToggleStates());
        } else {
            skb.enableToggleState(state, false);
        }

        mSoftKeyboardView.invalidate();
    }

    private void updateSkbLayout() {
        SkbPool      skbPool      = SkbPool.getInstance();
        SoftKeyboard softKeyboard = null; // XML中读取保存的键值.
        switch (mSkbLayout) {
            case R.xml.sbd_qwerty: // 全英文键盘.
                softKeyboard = skbPool.getSoftKeyboard(mContext, R.xml.sbd_qwerty);
                break;
            case R.xml.sbd_number: // 数字键盘.
                softKeyboard = skbPool.getSoftKeyboard(mContext, R.xml.sbd_number);
                break;
            default:
                softKeyboard = skbPool.getSoftKeyboard(mContext, R.xml.sbd_qwerty);
                break;
        }
        // 键盘的值.(英文键盘，大小写标志位)
        mInputModeSwitcher.getToggleStates().mQwertyUpperCase = softKeyboard.isQwertyUpperCase();
        mInputModeSwitcher.getToggleStates().mQwerty = softKeyboard.isQwerty();
        mInputModeSwitcher.getToggleStates().mPageState = InputModeSwitcher.TOGGLE_KEYCODE_PAGE_1;
        mInputModeSwitcher.getToggleStates().mQwertyPinyin = false;
        // 这样可以用于切换.(反位)
        softKeyboard.setQwertyUpperCase(!softKeyboard.isQwertyUpperCase());
        // 更新状态切换.
        mInputModeSwitcher.prepareToggleStates(null);
        //
        mSoftKeyboardView = (SoftKeyboardView) findViewById(R.id.softKeyboardView);
        // 重新绘制 软键盘.
        mSoftKeyboardView.setSoftKeyboard(softKeyboard);
        mInputModeSwitcher.getToggleStates().mSwitchSkb = false;
    }

    SoftKeyBoardListener mSoftKeyListener;

    public void setOnSoftKeyBoardListener(SoftKeyBoardListener cb) {
        mSoftKeyListener = cb;
    }

    public void setDefualtSelectKey(int row, int index) {
        if (mSoftKeyboardView != null) {
            SoftKeyboard softKeyboard = mSoftKeyboardView.getSoftKeyboard();
            if (softKeyboard != null)
                softKeyboard.setOneKeySelected(row, index);
        }
    }

    /**
     * 按下按键的处理.
     */
    private boolean setKeyCodeEnter(SoftKey softKey) {
        if (softKey == null) {
            OPENLOG.E(TAG, "setKeyCodeEnter softKey is null");
            return true;
        }
        //
        int softKeyCode = softKey.getKeyCode();
        /**
         * 自定义按键，比如大/小写转换, 键盘切换等等.<br>
         * keyCode <= -1 <br>
         */
        if (softKey.isUserDefKey()) {
            OPENLOG.D(TAG, "setKeyCodeEnter isUserKey keyCode:" + softKeyCode);
            mInputModeSwitcher.switchModeForUserKey(softKey);
            updateInputMode(softKey); // 大/小 写切换.
            return true;
        }
        /*
         * 判断是否为 A 至 Z 的字母.
		 */
        if ((softKeyCode >= KeyEvent.KEYCODE_A && softKeyCode <= KeyEvent.KEYCODE_Z)
                || (softKeyCode >= KeyEvent.KEYCODE_0 && softKeyCode <= KeyEvent.KEYCODE_9)) {
            if (mInputModeSwitcher.isChineseText()){

                int keyChar = 0;
                int keyCode=softKeyCode;
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    keyChar = keyCode - KeyEvent.KEYCODE_A + 'a';
                } else if (keyCode >= KeyEvent.KEYCODE_0
                        && keyCode <= KeyEvent.KEYCODE_9) {
                    keyChar = keyCode - KeyEvent.KEYCODE_0 + '0';
                } else if (keyCode == KeyEvent.KEYCODE_COMMA) {
                    keyChar = ',';
                } else if (keyCode == KeyEvent.KEYCODE_PERIOD) {
                    keyChar = '.';
                } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    keyChar = ' ';
                } else if (keyCode == KeyEvent.KEYCODE_APOSTROPHE) {
                    keyChar = '\'';
                }

                if (mImeState == IMEService.ImeState.STATE_IDLE
                        || mImeState == IMEService.ImeState.STATE_APP_COMPLETION) {
                    OPENLOG.D(TAG,"IMEService.ImeState.STATE_IDLE");
                    mImeState = IMEService.ImeState.STATE_IDLE;
                    return processStateIdle(keyChar, keyCode);
                } else if (mImeState == IMEService.ImeState.STATE_INPUT) {
                    OPENLOG.D(TAG,"IMEService.ImeState.STATE_INPUT");
                    return processStateInput(keyChar, keyCode);
                } else if (mImeState == IMEService.ImeState.STATE_PREDICT) {
                    OPENLOG.D(TAG,"IMEService.ImeState.STATE_PREDICT");
                    return processStatePredict(keyChar, keyCode);
                } else if (mImeState == IMEService.ImeState.STATE_COMPOSING) {
                    OPENLOG.D(TAG,"IMEService.ImeState.STATE_COMPOSING");
                    return processStateEditComposing(keyChar, keyCode);
                }
            }else{
                OPENLOG.D(TAG,"IMEService.commitResultText");
                String label = softKey.getKeyLabel();
                mService.commitResultText(label);
                return true;
            }
        }
		/*
		 * 处理按键的删除,回车,空格. <br> 光标移动. 返回.
		 */
        switch (softKeyCode) {
            case KeyEvent.KEYCODE_DEL: // 删除 67
                mService.getCurrentInputConnection().deleteSurroundingText(1, 0);
                break;
            case KeyEvent.KEYCODE_ENTER: // 回车 66
                if (softKey instanceof ToggleSoftKey) {
                    ToggleSoftKey toggleSoftKey = (ToggleSoftKey) softKey;
				/*
				 * 多行文本下，只发送'\n'，让文本换行.
				 */
                    if (toggleSoftKey.getSaveStateId() == InputModeSwitcher.TOGGLE_ENTER_MULTI_LINE_DONE) {
                        mService.commitResultText("\n");
                    } else {
                        mService.sendKeyChar('\n');
                    }
                }
                break;
            case KeyEvent.KEYCODE_SPACE: // 空格 62
                mService.sendKeyChar(' ');
                break;
            case KeyEvent.KEYCODE_BACK: // 返回
                mService.requestHideSelf(0); // 输入法88.
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT: // 光标向左移动.
                mService.setCursorLeftMove();
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: // 光标向右移动.
                mService.setCursorRightMove();
                break;
            default: // 测试.
//                String label = softKey.getKeyLabel();
//                mService.commitResultText(label);
                break;
        }

        if (mSoftKeyListener != null) {
            mSoftKeyListener.onCommitText(softKey);
        }

        return true;
    }

    /**
     * 防止输入法被重复退出.
     */
    private boolean isBackQuit = false;

    /**
     * 处理DOWN事件.
     */
    public boolean onSoftKeyDown(int keyCode, KeyEvent event) {
        OPENLOG.D(TAG, ":::" + mInputModeSwitcher.isChineseText());

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                SoftKeyboard softKeyboard = mSoftKeyboardView.getSoftKeyboard();
                SoftKey softKey = softKeyboard.getSelectSoftKey();
                mSoftKeyboardView.setSoftKeyPress(true);
                if (softKey.getKeyCode() == KeyEvent.KEYCODE_BACK)
                    isBackQuit = true;
                if (softKey.getKeyCode() != KeyEvent.KEYCODE_BACK && !setKeyCodeEnter(softKey)) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL: // 删除
                //todo:当为中文输入模式的处理
                mService.getCurrentInputConnection().deleteSurroundingText(1, 0);
                break;
            case KeyEvent.KEYCODE_BACK: // 返回
            case KeyEvent.KEYCODE_ESCAPE: // 键盘返回.
                return false;
            case KeyEvent.KEYCODE_DPAD_LEFT: // 左
            case KeyEvent.KEYCODE_DPAD_RIGHT: // 右
            case KeyEvent.KEYCODE_DPAD_UP: // 上
            case KeyEvent.KEYCODE_DPAD_DOWN: // 下
                mSoftKeyboardView.setSoftKeyPress(false);
                actionForKeyEvent(keyCode); // 按键移动.
            default:
                // 处理键盘按键.
                return false;
        }

        return true;
    }

    /**
     * 处理UP的事件.
     */
    public boolean onSoftKeyUp(int keyCode, KeyEvent event) {
        if (mSoftKeyboardView != null)
            mSoftKeyboardView.setSoftKeyPress(false);
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE: // 键盘返回.
                return false;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                SoftKeyboard softKeyboard = null;
                if (mSoftKeyboardView != null) {
                    softKeyboard = mSoftKeyboardView.getSoftKeyboard();
                    SoftKey softKey = softKeyboard.getSelectSoftKey();
                    if (isBackQuit && (softKey.getKeyCode() == KeyEvent.KEYCODE_BACK) && setKeyCodeEnter(softKey)) {
                        mService.requestHideSelf(0); // 输入法88.
                        isBackQuit = false;
                    }
                }
                break;
        }
        return true;
    }

    /**
     * 根据 上，下，左，右 来绘制按键位置.
     */
    public boolean actionForKeyEvent(int direction) {
        return mSoftKeyboardView != null && mSoftKeyboardView.moveToNextKey(direction);
    }

    private static final int LOG_PRESS_DELAYMILLIS = 200;

    Handler longPressHandler = new Handler() {
        public void handleMessage(Message msg) {
            SoftKey downSKey = (SoftKey) msg.obj;
            if (downSKey != null) {
                setKeyCodeEnter(downSKey);
                // 长按按键.(继续发送) 知道松开按键.
                Message msg1 = longPressHandler.obtainMessage();
                msg1.obj = downSKey;
                longPressHandler.sendMessageDelayed(msg1, LOG_PRESS_DELAYMILLIS);
            }
        }

        ;
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        int x      = (int) event.getX();
        int y      = (int) event.getY();


        switch (action) {
            case MotionEvent.ACTION_DOWN:
                SoftKey downSKey = mSoftKeyboardView.onTouchKeyPress(x, y);
                if (downSKey != null) {
                    int keyCode = downSKey.getKeyCode();
                    mSoftKeyboardView.getSoftKeyboard().setOneKeySelected(downSKey);
                    mSoftKeyboardView.setSoftKeyPress(true);
                    setKeyCodeEnter(downSKey);
                    // 长按按键.
                    Message msg = longPressHandler.obtainMessage();
                    msg.obj = downSKey;
                    longPressHandler.sendMessageDelayed(msg, LOG_PRESS_DELAYMILLIS);
                }
                break;
            case MotionEvent.ACTION_UP:
                longPressHandler.removeCallbacksAndMessages(null); // 取消长按按键.
                mSoftKeyboardView.setSoftKeyPress(false);
                break;
        }
        return true;
    }

    /**
     * 当 mImeState == ImeState.STATE_IDLE 或者 mImeState ==
     * ImeState.STATE_APP_COMPLETION 时的按键处理函数
     *
     * @param keyChar
     * @param keyCode
     * @return
     */
    private boolean processStateIdle(int keyChar, int keyCode) {
        // In this status, when user presses keys in [a..z], the status will
        // change to input state.
        if (keyChar >= 'a' && keyChar <= 'z') {
//            if (!realAction)
//                return true;
            mService.mDecInfo.addSplChar((char) keyChar, true);

            // 对输入的拼音进行查询
            mService.chooseAndUpdate(-1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
//            if (!realAction)
//                return true;
                // 模拟删除键发送给 EditText
            simulateKeyEventDownUp(keyCode);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
//            if (!realAction)
//                return true;

            // 发送 ENTER 键给 EditText
            mService.sendKeyChar('\n');
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_ALT_LEFT
                || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
                || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            return true;
        }
//        else if (keyChar != 0 && keyChar != '\t') {
//            if (realAction) {
//                if (keyChar == ',' || keyChar == '.') {
//                    // 发送 '\uff0c' 或者 '\u3002' 给EditText
//                    mService.inputCommaPeriod("", keyChar, false, IMEService.ImeState.STATE_IDLE);
//                } else {
//                    if (0 != keyChar) {
//                        String result = String.valueOf((char) keyChar);
//                        mService.commitResultText(result);
//                    }
//                }
//            }
//            return true;
//        }
        return false;
    }

    /**
     * 当 mImeState == ImeState.STATE_INPUT 时的按键处理函数
     *
     * @param keyChar
     * @param keyCode
     * @return
     */
    private boolean processStateInput(int keyChar, int keyCode) {

        if (keyChar >= 'a' && keyChar <= 'z' || keyChar == '\''
                && !mService.mDecInfo.charBeforeCursorIsSeparator()
                || keyCode == KeyEvent.KEYCODE_DEL) {
//            if (!realAction)
//                return true;

            // 添加输入的拼音，然后进行词库查询，或者删除输入的拼音指定的字符或字符串，然后进行词库查询。
            return mService.processSurfaceChange(keyChar, keyCode);
        } else if (keyChar == ',' || keyChar == '.') {
//            if (!realAction)
//                return true;

            // 发送 '\uff0c' 或者 '\u3002' 给EditText
            mService.inputCommaPeriod(mService.mDecInfo.getCurrentFullSent(mService.mCandidatesContainer
                            .getActiveCandiatePos()), keyChar, true,
                    IMEService.ImeState.STATE_IDLE);
            return true;

        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
//            if (!realAction)
//                return true;

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // 高亮位置向上一个候选词移动或者移动到上一页的最后一个候选词的位置。
                mService.mCandidatesContainer.activeCurseBackward();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // 高亮位置向下一个候选词移动或者移动到下一页的第一个候选词的位置。
                mService.mCandidatesContainer.activeCurseForward();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // If it has been the first page, a up key will shift
                // the state to edit composing string.
                // 到上一页候选词
                if (!mService.mCandidatesContainer.pageBackward(false, true)) {
                    mService.mCandidatesContainer.enableActiveHighlight(false);
                    mService.changeToStateComposing(true);
                    mService.updateComposingText(true);
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 到下一页候选词
                mService.mCandidatesContainer.pageForward(false, true);
            }
            return true;
        } else if (keyCode >= KeyEvent.KEYCODE_1
                && keyCode <= KeyEvent.KEYCODE_9) {
//            if (!realAction)
//                return true;

            int activePos = keyCode - KeyEvent.KEYCODE_1;
            int currentPage = mService.mCandidatesContainer.getCurrentPage();
            if (activePos < mService.mDecInfo.getCurrentPageSize(currentPage)) {
                activePos = activePos
                        + mService.mDecInfo.getCurrentPageStart(currentPage);
                if (activePos >= 0) {
                    // 选择候选词，并根据条件是否进行下一步的预报。
                    mService.chooseAndUpdate(activePos);
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
//            if (!realAction)
//                return true;
//            if (mInputModeSwitcher.isEnterNoramlState()) {
//                // 把输入的拼音字符串发送给EditText
//                mService.commitResultText(mService.mDecInfo.getOrigianlSplStr().toString());
//                mService.resetToIdleState(false);
//            } else {
                // 把高亮的候选词发送给EditText
                mService.commitResultText(mService.mDecInfo
                        .getCurrentFullSent(mService.mCandidatesContainer
                                .getActiveCandiatePos()));
                // 把ENTER发送给EditText
                mService.sendKeyChar('\n');
                mService.resetToIdleState(false);
//            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_SPACE) {
//            if (!realAction)
//                return true;
            // 选择高亮的候选词
            mService.chooseCandidate(-1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
//            if (!realAction)
//                return true;
            mService.resetToIdleState(false);
            // 关闭输入法
            mService.requestHideSelf(0);
            return true;
        }
        return false;
    }

    /**
     * 当 mImeState == ImeState.STATE_PREDICT 时的按键处理函数
     *
     * @param keyChar
     * @param keyCode
     * @return
     */
    private boolean processStatePredict(int keyChar, int keyCode) {

        // In this status, when user presses keys in [a..z], the status will
        // change to input state.
        if (keyChar >= 'a' && keyChar <= 'z') {
            mService.changeToStateInput(true);
            // 加一个字符进输入的拼音字符串中
            mService.mDecInfo.addSplChar((char) keyChar, true);
            // 对输入的拼音进行查询。
            mService.chooseAndUpdate(-1);
        } else if (keyChar == ',' || keyChar == '.') {
            // 发送 '\uff0c' 或者 '\u3002' 给EditText
            mService.inputCommaPeriod("", keyChar, true, IMEService.ImeState.STATE_IDLE);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // 高亮位置向上一个候选词移动或者移动到上一页的最后一个候选词的位置。
                mService.mCandidatesContainer.activeCurseBackward();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // 高亮位置向下一个候选词移动或者移动到下一页的第一个候选词的位置。
                mService.mCandidatesContainer.activeCurseForward();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // 到上一页候选词
                mService.mCandidatesContainer.pageBackward(false, true);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 到下一页候选词
                mService.mCandidatesContainer.pageForward(false, true);
            }
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            mService.resetToIdleState(false);
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            mService.resetToIdleState(false);
            // 关闭输入法
            mService.requestHideSelf(0);
        } else if (keyCode >= KeyEvent.KEYCODE_1
                && keyCode <= KeyEvent.KEYCODE_9) {
            int activePos = keyCode - KeyEvent.KEYCODE_1;
            int currentPage = mService.mCandidatesContainer.getCurrentPage();
            if (activePos < mService.mDecInfo.getCurrentPageSize(currentPage)) {
                activePos = activePos
                        + mService.mDecInfo.getCurrentPageStart(currentPage);
                if (activePos >= 0) {
                    // 选择候选词
                    mService.chooseAndUpdate(activePos);
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            // 发生ENTER键给EditText
            mService.sendKeyChar('\n');
            mService.resetToIdleState(false);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            // 选择候选词
            mService.chooseCandidate(-1);
        }

        return true;
    }

    /**
     * 当 mImeState == ImeState.STATE_COMPOSING 时的按键处理函数
     *
     * @param keyChar
     * @param keyCode
     * @return
     */
    private boolean processStateEditComposing(int keyChar, int keyCode) {

        // 获取输入的音字符串的状态
        ComposingView.ComposingStatus cmpsvStatus = mService.mComposingView
                .getComposingStatus();



        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!mService.mDecInfo.selectionFinished()) {
                mService.changeToStateInput(true);
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // 移动候选词的光标
            mService.mComposingView.moveCursor(keyCode);
        }  else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            String retStr;
            if (!mService.mDecInfo.isCandidatesListEmpty()) {
                // 获取当前高亮的候选词
                retStr = mService.mDecInfo.getCurrentFullSent(mService.mCandidatesContainer
                        .getActiveCandiatePos());
            } else {
                // 获取组合的输入拼音的字符（有可能存在选中的候选词）
                retStr = mService.mDecInfo.getComposingStr();
            }
            // 发送文本给EditText
            mService.commitResultText(retStr);
            // 发生ENTER键给EditText
            mService.sendKeyChar('\n');
            mService.resetToIdleState(false);
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            mService.resetToIdleState(false);
            // 关闭输入法
            mService.requestHideSelf(0);
            return true;
        } else {
            // 添加输入的拼音，然后进行词库查询，或者删除输入的拼音指定的字符或字符串，然后进行词库查询。
            return mService.processSurfaceChange(keyChar, keyCode);
        }
        return true;
    }

    /**
     * 定制软键盘的，高度和高度.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        OPENLOG.D(TAG, "onMeasure");
        MeasureHelper measureHelper  = MeasureHelper.getInstance();
        int           measuredWidth  = measureHelper.getScreenWidth();
        int           measuredHeight = getPaddingTop();
        measuredHeight += measureHelper.getSkbHeight();
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 模拟按下一个按键
     *
     * @param keyCode
     */
    private void simulateKeyEventDownUp(int keyCode) {
        InputConnection ic = mService.getCurrentInputConnection();
        if (null == ic)
            return;

        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
}
