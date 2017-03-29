package com.chen.openinputmethodtest.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.chen.openinputmethodtest.service.IMEService;
import com.open.inputmethod.R;

/**
 * @author chankey
 * @creatTime 2017/3/14
 * @descript ${DESCRIPT}
 */

public class ComposingView extends View {
    public enum ComposingStatus {
        SHOW_PINYIN, SHOW_STRING_LOWERCASE, EDIT_PINYIN,
    }

    private static final int LEFT_RIGHT_MARGIN = 5;

    /**
     * Used to draw composing string. When drawing the active and idle part of
     * the spelling(Pinyin) string, the color may be changed.
     */
    private Paint mPaint;

    /**
     * Drawable used to draw highlight effect. 高亮
     */
    private Drawable mHlDrawable;

    /**
     * Drawable used to draw cursor for editing mode. 光标
     */
    private Drawable mCursor;

    /**
     * Used to estimate dimensions to show the string .
     */
    private Paint.FontMetricsInt mFmi;

    private int mStrColor; // 字符串普通颜色
    private int mStrColorHl; // 字符串高亮颜色
    private int mStrColorIdle; // 字符串空闲颜色

    private int mFontSize; // 字体大小

    /**
     * 获拼音字符串的状态
     */
    private ComposingStatus mComposingStatus;

    /**
     * 解码操作对象
     */
    DecodingInfo mDecInfo;

    public ComposingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources r = context.getResources();
        mHlDrawable = r.getDrawable(R.drawable.composing_hl_bg);
        mCursor = r.getDrawable(R.drawable.composing_area_cursor);

        mStrColor = r.getColor(R.color.composing_color);
        mStrColorHl = r.getColor(R.color.composing_color_hl);
        mStrColorIdle = r.getColor(R.color.composing_color_idle);

        mFontSize = r.getDimensionPixelSize(R.dimen.composing_height);

        mPaint = new Paint();
        mPaint.setColor(mStrColor);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(mFontSize);

        mFmi = mPaint.getFontMetricsInt();
    }

    /**
     * 重置拼音字符串View状态
     */
    public void reset() {
        mComposingStatus = ComposingStatus.SHOW_PINYIN;
    }

    /**
     * 设置 解码操作对象，然后刷新View。
     *
     * @param decInfo
     * @param imeStatus
     */
    public void setDecodingInfo(DecodingInfo decInfo,
                                IMEService.ImeState imeStatus) {
        mDecInfo = decInfo;

        if (IMEService.ImeState.STATE_INPUT == imeStatus) {
            mComposingStatus = ComposingStatus.SHOW_PINYIN;
            mDecInfo.moveCursorToEdge(false);
        } else {
            if (decInfo.getFixedLen() != 0
                    || ComposingStatus.EDIT_PINYIN == mComposingStatus) {
                mComposingStatus = ComposingStatus.EDIT_PINYIN;
            } else {
                mComposingStatus = ComposingStatus.SHOW_STRING_LOWERCASE;
            }
            mDecInfo.moveCursor(0);
        }

        measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        requestLayout();
        invalidate();
    }

    /**
     * 移动光标。其实可以算是对KeyEvent.KEYCODE_DPAD_LEFT和KeyEvent.
     * KEYCODE_DPAD_RIGHT这两个键的处理函数。
     *
     * @param keyCode
     * @return
     */
    public boolean moveCursor(int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)
            return false;

        if (ComposingStatus.EDIT_PINYIN == mComposingStatus) {
            int offset = 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                offset = -1;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                offset = 1;
            mDecInfo.moveCursor(offset);
        } else if (ComposingStatus.SHOW_STRING_LOWERCASE == mComposingStatus) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mComposingStatus = ComposingStatus.EDIT_PINYIN;

                measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                requestLayout();
            }

        }
        //todo:当按方向键下键时光标移动到键盘view上
        invalidate();
        return true;
    }

    /**
     * 获取输入的音字符串的状态
     *
     * @return
     */
    public ComposingStatus getComposingStatus() {
        return mComposingStatus;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float width;
        int height;
        height = mFmi.bottom - mFmi.top + getPaddingTop() + getPaddingBottom();

        if (null == mDecInfo) {
            width = 0;
        } else {
            width = getPaddingLeft() + getPaddingRight() + LEFT_RIGHT_MARGIN
                    * 2;

            String str;
            if (ComposingStatus.SHOW_STRING_LOWERCASE == mComposingStatus) {
                str = mDecInfo.getOrigianlSplStr().toString();
            } else {
                str = mDecInfo.getComposingStrForDisplay();
            }
            width += mPaint.measureText(str, 0, str.length());
        }
        setMeasuredDimension((int) (width + 0.5f), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ComposingStatus.EDIT_PINYIN == mComposingStatus
                || ComposingStatus.SHOW_PINYIN == mComposingStatus) {
            drawForPinyin(canvas);
            return;
        }

        // 画选中的候选词
        float x, y;
        x = getPaddingLeft() + LEFT_RIGHT_MARGIN;
        y = -mFmi.top + getPaddingTop();

        mPaint.setColor(mStrColorHl);
        mHlDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth()
                - getPaddingRight(), getHeight() - getPaddingBottom());
        mHlDrawable.draw(canvas);

        String splStr = mDecInfo.getOrigianlSplStr().toString();
        canvas.drawText(splStr, 0, splStr.length(), x, y, mPaint);
    }

    /**
     * 画光标
     *
     * @param canvas
     * @param x
     */
    private void drawCursor(Canvas canvas, float x) {
        mCursor.setBounds((int) x, getPaddingTop(),
                (int) x + mCursor.getIntrinsicWidth(), getHeight()
                        - getPaddingBottom());
        mCursor.draw(canvas);
    }

    /**
     * 画拼音字符串
     *
     * @param canvas
     */
    private void drawForPinyin(Canvas canvas) {
        float x, y;
        x = getPaddingLeft() + LEFT_RIGHT_MARGIN;
        y = -mFmi.top + getPaddingTop();

        mPaint.setColor(mStrColor);

        int cursorPos = mDecInfo.getCursorPosInCmpsDisplay();
        int cmpsPos = cursorPos;
        String cmpsStr = mDecInfo.getComposingStrForDisplay();
        int activeCmpsLen = mDecInfo.getActiveCmpsDisplayLen();
        if (cursorPos > activeCmpsLen)
            cmpsPos = activeCmpsLen;
        canvas.drawText(cmpsStr, 0, cmpsPos, x, y, mPaint);
        x += mPaint.measureText(cmpsStr, 0, cmpsPos);
        if (cursorPos <= activeCmpsLen) {
            if (ComposingStatus.EDIT_PINYIN == mComposingStatus) {
                drawCursor(canvas, x);
            }
            canvas.drawText(cmpsStr, cmpsPos, activeCmpsLen, x, y, mPaint);
        }

        x += mPaint.measureText(cmpsStr, cmpsPos, activeCmpsLen);

        if (cmpsStr.length() > activeCmpsLen) {
            mPaint.setColor(mStrColorIdle);
            int oriPos = activeCmpsLen;
            if (cursorPos > activeCmpsLen) {
                if (cursorPos > cmpsStr.length())
                    cursorPos = cmpsStr.length();
                canvas.drawText(cmpsStr, oriPos, cursorPos, x, y, mPaint);
                x += mPaint.measureText(cmpsStr, oriPos, cursorPos);

                if (ComposingStatus.EDIT_PINYIN == mComposingStatus) {
                    drawCursor(canvas, x);
                }

                oriPos = cursorPos;
            }
            canvas.drawText(cmpsStr, oriPos, cmpsStr.length(), x, y, mPaint);
        }
    }
}
