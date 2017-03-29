package com.chen.openinputmethodtest.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.open.inputmethod.R;

import java.util.Vector;

/**
 * @author chankey
 *  2017/3/8
 *  保留在上方的候选词视图
 */

public class Candidateview extends View {
    //一个item最小的宽度
    private static final float  MIN_ITEM_WIDTH    = 22;
    // 省略号
    private static final String SUSPENSION_POINTS = "...";
    //候选词区域的宽度
    private int mContentWidth;
    // 候选词区域的高度
    private int mContentHeight;
    // 是否显示附注。附注是当硬键盘有效的时候显示的。
    private boolean mShowFootnote              = true;
    // 词库解码对象
    private DecodingInfo mDecInfo;
    /* 箭头更新接口。在onDraw（）中，当mUpdateArrowStatusWhenDraw为true，
     * 该接口的updateArrowStatus（）方法被调用。因为箭头是放在候选词集装箱中的，不是放在候选词视图中。
     */
    private ArrowUpdater mArrowUpdater;

    //在onDraw（）的时候是否更新箭头
    private boolean mUpdateArrowStatusWhenDraw = false;
    //候选词视图显示的页码
    private int mPageNo;
    //活动（高亮）的候选词在页面的位置。
    private int mActiveCandInPage;
    //是否高亮活动的候选词
    private boolean mEnableActiveHighlight = true;
    //刚刚计算的页码
    private int     mPageNoCalculated      = -1;
    //高亮显示的图片
    private Drawable mActiveCellDrawable;
    //分隔符图片
    private Drawable mSeparatorDrawable;
    //正常候选词的颜色，来自输入法词库的候选词。
    private int      mImeCandidateColor;
    //推荐候选词的颜色，推荐的候选词是来自APP的。
    private int      mRecommendedCandidateColor;
    //候选词的颜色，它可以是 mImeCandidateColor 和 mRecommendedCandidateColor 其中的一个。
    private int      mNormalCandidateColor;
    // from IME and candidates from application. 高亮候选词的颜色
    private int      mActiveCandidateColor;
    //正常候选词的文本大小，来自输入法词库的候选词。
    private int      mImeCandidateTextSize;
    //推荐候选词的文本大小，推荐的候选词是来自APP的。
    private int      mRecommendedCandidateTextSize;
    /* 候选词的文本大小，它可以是 mImeCandidateTextSize 和 mRecommendedCandidateTextSize
    * 其中的一个。
    */
    private int      mCandidateTextSize;
    //候选词的画笔
    private Paint    mCandidatesPaint;
    //附注的画笔
    private Paint    mFootnotePaint;
    // 省略号的宽度
    private float    mSuspensionPointsWidth;
    //活动（高亮）候选词的区域
    private RectF    mActiveCellRect;
    //候选词的左边和右边间隔
    private float    mCandidateMargin;
    // 候选词的左边和右边的额外间隔
    private float    mCandidateMarginExtra;

    // 在本页候选词的区域向量列表
    private Vector<RectF> mCandRects;

    //      候选词的字体测量对象
    private Paint.FontMetricsInt mFmiCandidates;
    /**
     * 按下某个候选词的定时器。
     */
    private PressTimer mTimer = new PressTimer();

    public Candidateview(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        //获取一系列的资源文件
        Resources r = context.getResources();
        //判断
        Configuration conf = r.getConfiguration();
        if (conf.keyboard == Configuration.KEYBOARD_NOKEYS
                || conf.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            mShowFootnote = false;
        }

        mActiveCellDrawable = r.getDrawable(R.drawable.softkey_bg_select2);
        mSeparatorDrawable = r.getDrawable(R.drawable.candidates_vertical_line);
        mCandidateMargin = r.getDimension(R.dimen.candidate_margin_left_right);

        mImeCandidateColor = r.getColor(R.color.candidate_color);
        mRecommendedCandidateColor = r.getColor(R.color.recommended_candidate_color);
        mNormalCandidateColor = mImeCandidateColor;
        mActiveCandidateColor = r.getColor(R.color.active_candidate_color);

        mCandidatesPaint = new Paint();
        mCandidatesPaint.setAntiAlias(true);

        mFootnotePaint = new Paint();
        mFootnotePaint.setAntiAlias(true);
        mFootnotePaint.setColor(r.getColor(R.color.footnote_color));
        mActiveCellRect = new RectF();

        mCandRects = new Vector<RectF>();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mOldWidth = getMeasuredWidth();
        int mOldHeight = getMeasuredHeight();

        setMeasuredDimension(
                getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));

        if (mOldWidth != getMeasuredWidth()
                || mOldHeight != getMeasuredHeight()) {
            onSizeChanged();
        }
    }

    /**
     * 初始化。
     *
     * @param arrowUpdater
     */
    public void initialize(ArrowUpdater arrowUpdater) {
        mArrowUpdater = arrowUpdater;
    }

    /**
     * 根据候选词的来源设置候选词使用的颜色和文本大小，并计算省略号的宽度。
     *
     * @param decInfo
     */
    public void setDecodingInfo(DecodingInfo decInfo) {
        if (null == decInfo)
            return;
        mDecInfo = decInfo;
        mPageNoCalculated = -1;

        // 根据候选词来源设置候选词使用的颜色和文本大小
        if (mDecInfo.candidatesFromApp()) {
            mNormalCandidateColor = mRecommendedCandidateColor;
            mCandidateTextSize = mRecommendedCandidateTextSize;
        } else {
            mNormalCandidateColor = mImeCandidateColor;
            mCandidateTextSize = mImeCandidateTextSize;
        }

        if (mCandidatesPaint.getTextSize() != mCandidateTextSize) {
            // 计算省略号宽度
            mCandidatesPaint.setTextSize(mCandidateTextSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
            mSuspensionPointsWidth = mCandidatesPaint
                    .measureText(SUSPENSION_POINTS);
        }

        // Remove any pending timer for the previous list.
        mTimer.removeTimer();
    }

    /**
     * 获取活动（高亮）的候选词在页面的位置。
     *
     * @return
     */
    public int getActiveCandiatePosInPage() {
        return mActiveCandInPage;
    }

    /**
     * 获取活动（高亮）的候选词在所有候选词中的位置
     *
     * @return
     */
    public int getActiveCandiatePosGlobal() {
        return mDecInfo.mPageStart.get(mPageNo) + mActiveCandInPage;
    }

    /**
     * Show a page in the decoding result set previously.
     *
     * @param pageNo
     *            Which page to show.
     * @param activeCandInPage
     *            Which candidate should be set as active item.
     * @param enableActiveHighlight
     *            When false, active item will not be highlighted.
     */
    /**
     * 显示指定页的候选词
     *
     * @param pageNo
     * @param activeCandInPage
     * @param enableActiveHighlight
     */
    public void showPage(int pageNo, int activeCandInPage,
                         boolean enableActiveHighlight) {
        if (null == mDecInfo)
            return;
        mPageNo = pageNo;
        mActiveCandInPage = activeCandInPage;
        if (mEnableActiveHighlight != enableActiveHighlight) {
            mEnableActiveHighlight = enableActiveHighlight;
        }

        if (!calculatePage(mPageNo)) {
            mUpdateArrowStatusWhenDraw = true;
        } else {
            mUpdateArrowStatusWhenDraw = false;
        }

        invalidate();
    }

    /**
     * 设置是否高亮候选词
     *
     * @param enableActiveHighlight
     */
    public void enableActiveHighlight(boolean enableActiveHighlight) {
        if (enableActiveHighlight == mEnableActiveHighlight)
            return;

        mEnableActiveHighlight = enableActiveHighlight;
        invalidate();
    }

    /**
     * 高亮位置向下一个候选词移动。
     *
     * @return
     */
    public boolean activeCursorForward() {
        if (!mDecInfo.pageReady(mPageNo))
            return false;
        int pageSize = mDecInfo.mPageStart.get(mPageNo + 1)
                - mDecInfo.mPageStart.get(mPageNo);
        if (mActiveCandInPage + 1 < pageSize) {
            showPage(mPageNo, mActiveCandInPage + 1, true);
            return true;
        }
        return false;
    }

    /**
     * 高亮位置向上一个候选词移动。
     *
     * @return
     */
    public boolean activeCurseBackward() {
        if (mActiveCandInPage > 0) {
            showPage(mPageNo, mActiveCandInPage - 1, true);
            return true;
        }
        return false;
    }

    /**
     * 计算候选词区域的宽度和高度、候选词文本大小、附注文本大小、省略号宽度。当尺寸发生改变时调用。在onMeasure（）中调用。
     */
    private void onSizeChanged() {
        // 计算候选词区域的宽度和高度
        mContentWidth = getMeasuredWidth() - getPaddingLeft()
                - getPaddingRight();
        mContentHeight = (int) ((getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) * 0.95f);

        /**
         * How to decide the font size if the height for display is given? Now
         * it is implemented in a stupid way.
         */
        // 根据候选词区域高度来计算候选词应该使用的文本大小
        int textSize = 1;
        mCandidatesPaint.setTextSize(textSize);
        mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
        while (mFmiCandidates.bottom - mFmiCandidates.top < mContentHeight) {
            textSize++;
            mCandidatesPaint.setTextSize(textSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
        }

        // 设置计算出的候选词文本大小
        mImeCandidateTextSize = textSize;
        mRecommendedCandidateTextSize = textSize * 3 / 4;
        if (null == mDecInfo) {
            // 计算省略号的宽度
            mCandidateTextSize = mImeCandidateTextSize;
            mCandidatesPaint.setTextSize(mCandidateTextSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
            mSuspensionPointsWidth = mCandidatesPaint
                    .measureText(SUSPENSION_POINTS);
        } else {
            // Reset the decoding information to update members for painting.
            setDecodingInfo(mDecInfo);
        }

        // 计算附注文本的大小
        textSize = 1;
        mFootnotePaint.setTextSize(textSize);
        Paint.FontMetricsInt nFmiFootnote = mFootnotePaint.getFontMetricsInt();
        while (nFmiFootnote.bottom - nFmiFootnote.top < mContentHeight / 2) {
            textSize++;
            mFootnotePaint.setTextSize(textSize);
            nFmiFootnote = mFootnotePaint.getFontMetricsInt();
        }
        textSize--;
        mFootnotePaint.setTextSize(textSize);
        nFmiFootnote = mFootnotePaint.getFontMetricsInt();

        // When the size is changed, the first page will be displayed.
        mPageNo = 0;
        mActiveCandInPage = 0;
    }

    /**
     * 对还没有分页的候选词进行分页，计算指定页的候选词左右的额外间隔。
     *
     * @param pageNo
     * @return
     */
    private boolean calculatePage(int pageNo) {
        if (pageNo == mPageNoCalculated)
            return true;

        // 计算候选词区域宽度和高度
        mContentWidth = getMeasuredWidth() - getPaddingLeft()
                - getPaddingRight();
        mContentHeight = (int) ((getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) * 0.95f);

        if (mContentWidth <= 0 || mContentHeight <= 0)
            return false;

        // 候选词列表的size，即候选词的数量。
        int candSize = mDecInfo.mCandidatesList.size();

        // If the size of page exists, only calculate the extra margin.
        boolean onlyExtraMargin = false;
        int fromPage = mDecInfo.mPageStart.size() - 1;
        if (mDecInfo.mPageStart.size() > pageNo + 1) {
            // pageNo是最后一页之前的页码，不包括最后一页
            onlyExtraMargin = true;
            fromPage = pageNo;
        }

        // If the previous pages have no information, calculate them first.
        for (int p = fromPage; p <= pageNo; p++) {
            int pStart = mDecInfo.mPageStart.get(p);
            int pSize = 0;
            int charNum = 0;
            float lastItemWidth = 0;

            float xPos;
            xPos = 0;
            xPos += mSeparatorDrawable.getIntrinsicWidth();
            while (xPos < mContentWidth && pStart + pSize < candSize) {
                int itemPos = pStart + pSize;
                String itemStr = mDecInfo.mCandidatesList.get(itemPos);
                float itemWidth = mCandidatesPaint.measureText(itemStr);
                if (itemWidth < MIN_ITEM_WIDTH)
                    itemWidth = MIN_ITEM_WIDTH;

                itemWidth += mCandidateMargin * 2;
                itemWidth += mSeparatorDrawable.getIntrinsicWidth();
                if (xPos + itemWidth < mContentWidth || 0 == pSize) {
                    xPos += itemWidth;
                    lastItemWidth = itemWidth;
                    pSize++;
                    charNum += itemStr.length();
                } else {
                    break;
                }
            }
            if (!onlyExtraMargin) {
                // pageNo是最后一页或者往后的一页，这里应该就是对候选词进行分页的地方，保证每页候选词都能正常显示。
                mDecInfo.mPageStart.add(pStart + pSize);
                mDecInfo.mCnToPage.add(mDecInfo.mCnToPage.get(p) + charNum);
            }

            // 计算候选词的左右间隔
            float marginExtra = (mContentWidth - xPos) / pSize / 2;

            if (mContentWidth - xPos > lastItemWidth) {
                // Must be the last page, because if there are more items,
                // the next item's width must be less than lastItemWidth.
                // In this case, if the last margin is less than the current
                // one, the last margin can be used, so that the
                // look-and-feeling will be the same as the previous page.
                if (mCandidateMarginExtra <= marginExtra) {
                    marginExtra = mCandidateMarginExtra;
                }
            } else if (pSize == 1) {
                marginExtra = 0;
            }
            mCandidateMarginExtra = marginExtra;
        }
        mPageNoCalculated = pageNo;
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // The invisible candidate view(the one which is not in foreground) can
        // also be called to drawn, but its decoding result and candidate list
        // may be empty.
        if (null == mDecInfo || mDecInfo.isCandidatesListEmpty())
            return;

        // Calculate page. If the paging information is ready, the function will
        // return at once.
        calculatePage(mPageNo);

        int pStart = mDecInfo.mPageStart.get(mPageNo);
        int pSize = mDecInfo.mPageStart.get(mPageNo + 1) - pStart;
        float candMargin = mCandidateMargin + mCandidateMarginExtra;
        if (mActiveCandInPage > pSize - 1) {
            mActiveCandInPage = pSize - 1;
        }

        mCandRects.removeAllElements();

        float xPos = getPaddingLeft();
        int yPos = (getMeasuredHeight() - (mFmiCandidates.bottom - mFmiCandidates.top))
                / 2 - mFmiCandidates.top;
        xPos += drawVerticalSeparator(canvas, xPos);
        for (int i = 0; i < pSize; i++) {
            float footnoteSize = 0;
            String footnote = null;
            if (mShowFootnote) {
                footnote = Integer.toString(i + 1);
                footnoteSize = mFootnotePaint.measureText(footnote);
                assert (footnoteSize < candMargin);
            }
            String cand = mDecInfo.mCandidatesList.get(pStart + i);
            float candidateWidth = mCandidatesPaint.measureText(cand);
            float centerOffset = 0;
            if (candidateWidth < MIN_ITEM_WIDTH) {
                centerOffset = (MIN_ITEM_WIDTH - candidateWidth) / 2;
                candidateWidth = MIN_ITEM_WIDTH;
            }

            float itemTotalWidth = candidateWidth + 2 * candMargin;

            // 画高亮背景
            if (mActiveCandInPage == i && mEnableActiveHighlight) {
                mActiveCellRect.set(xPos, getPaddingTop() + 1, xPos
                        + itemTotalWidth, getHeight() - getPaddingBottom() - 1);
                mActiveCellDrawable.setBounds((int) mActiveCellRect.left,
                        (int) mActiveCellRect.top, (int) mActiveCellRect.right,
                        (int) mActiveCellRect.bottom);
                mActiveCellDrawable.draw(canvas);
            }

            if (mCandRects.size() < pSize)
                mCandRects.add(new RectF());
            mCandRects.elementAt(i).set(xPos - 1, yPos + mFmiCandidates.top,
                    xPos + itemTotalWidth + 1, yPos + mFmiCandidates.bottom);

            // Draw footnote
            if (mShowFootnote) {
                // 画附注
                canvas.drawText(footnote, xPos + (candMargin - footnoteSize)
                        / 2, yPos, mFootnotePaint);
            }

            // Left margin
            xPos += candMargin;
            if (candidateWidth > mContentWidth - xPos - centerOffset) {
                cand = getLimitedCandidateForDrawing(cand, mContentWidth - xPos
                        - centerOffset);
            }
            if (mActiveCandInPage == i && mEnableActiveHighlight) {
                mCandidatesPaint.setColor(mActiveCandidateColor);
            } else {
                mCandidatesPaint.setColor(mNormalCandidateColor);
            }
            // 画候选词
            canvas.drawText(cand, xPos + centerOffset, yPos, mCandidatesPaint);

            // Candidate and right margin
            xPos += candidateWidth + candMargin;

            // Draw the separator between candidates.
            // 画分隔符
            xPos += drawVerticalSeparator(canvas, xPos);
        }

        // Update the arrow status of the container.
        if (null != mArrowUpdater && mUpdateArrowStatusWhenDraw) {
            mArrowUpdater.updateArrowStatus();
            mUpdateArrowStatusWhenDraw = false;
        }
    }

    /**
     * 截取要显示的候选词短语+省略号
     *
     * @param rawCandidate
     * @param widthToDraw
     * @return
     */
    private String getLimitedCandidateForDrawing(String rawCandidate,
                                                 float widthToDraw) {
        int subLen = rawCandidate.length();
        if (subLen <= 1)
            return rawCandidate;
        do {
            subLen--;
            float width = mCandidatesPaint.measureText(rawCandidate, 0, subLen);
            if (width + mSuspensionPointsWidth <= widthToDraw || 1 >= subLen) {
                return rawCandidate.substring(0, subLen) + SUSPENSION_POINTS;
            }
        } while (true);
    }

    /**
     * 画分隔符
     *
     * @param canvas
     * @param xPos
     * @return
     */
    private float drawVerticalSeparator(Canvas canvas, float xPos) {
        mSeparatorDrawable.setBounds((int) xPos, getPaddingTop(), (int) xPos
                + mSeparatorDrawable.getIntrinsicWidth(), getMeasuredHeight()
                - getPaddingBottom());
        mSeparatorDrawable.draw(canvas);
        return mSeparatorDrawable.getIntrinsicWidth();
    }

    /**
     * 返回坐标点所在或者离的最近的候选词区域在mCandRects的索引
     *
     * @param x
     * @param y
     * @return
     */
    private int mapToItemInPage(int x, int y) {
        // mCandRects.size() == 0 happens when the page is set, but
        // touch events occur before onDraw(). It usually happens with
        // monkey test.
        if (!mDecInfo.pageReady(mPageNo) || mPageNoCalculated != mPageNo
                || mCandRects.size() == 0) {
            return -1;
        }

        int pageStart = mDecInfo.mPageStart.get(mPageNo);
        int pageSize = mDecInfo.mPageStart.get(mPageNo + 1) - pageStart;
        if (mCandRects.size() < pageSize) {
            return -1;
        }

        // If not found, try to find the nearest one.
        float nearestDis = Float.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < pageSize; i++) {
            RectF r = mCandRects.elementAt(i);
            if (r.left < x && r.right > x && r.top < y && r.bottom > y) {
                return i;
            }
            float disx = (r.left + r.right) / 2 - x;
            float disy = (r.top + r.bottom) / 2 - y;
            float dis = disx * disx + disy * disy;
            if (dis < nearestDis) {
                nearestDis = dis;
                nearest = i;
            }
        }

        return nearest;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    /**
     * 按下某个候选词的定时器。主要是刷新页面，显示按下的候选词为高亮状态。
     *
     * @ClassName PressTimer
     * @author keanbin
     */
    private class PressTimer extends Handler implements Runnable {
        private boolean mTimerPending = false; // 是否在定时器运行期间
        private int mPageNoToShow; // 显示的页码
        private int mActiveCandOfPage; // 高亮候选词在页面的位置

        public PressTimer() {
            super();
        }

        public void startTimer(long afterMillis, int pageNo, int activeInPage) {
            mTimer.removeTimer();
            postDelayed(this, afterMillis);
            mTimerPending = true;
            mPageNoToShow = pageNo;
            mActiveCandOfPage = activeInPage;
        }

        public int getPageToShow() {
            return mPageNoToShow;
        }

        public int getActiveCandOfPageToShow() {
            return mActiveCandOfPage;
        }

        public boolean removeTimer() {
            if (mTimerPending) {
                mTimerPending = false;
                removeCallbacks(this);
                return true;
            }
            return false;
        }

        public boolean isPending() {
            return mTimerPending;
        }

        public void run() {
            if (mPageNoToShow >= 0 && mActiveCandOfPage >= 0) {
                // Always enable to highlight the clicked one.
                showPage(mPageNoToShow, mActiveCandOfPage, true);
                invalidate();
            }
            mTimerPending = false;
        }
    }
}
