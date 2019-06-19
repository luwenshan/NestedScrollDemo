package com.lws.nestedscrolldemo

import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.Scroller
import androidx.core.view.NestedScrollingParent2
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class NestedScrollingDetailContainer @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, defAttrStyle: Int = 0) :
    ViewGroup(context, attributeSet, defAttrStyle), NestedScrollingParent2 {

    companion object {
        const val TAG_NESTED_SCROLL_WEBVIEW = "nested_scroll_webview"
        const val TAG_NESTED_SCROLL_RECYCLERVIEW = "nested_scroll_recyclerview"
        const val FLYING_FROM_WEBVIEW_TO_PARENT = 0
        const val FLYING_FROM_PARENT_TO_WEBVIEW = 1
        const val FLYING_FROM_RVLIST_TO_PARENT = 2
    }

    private var mIsSetFlying = false
    private var mIsRvFlyingDown = false
    private var mIsBeingDragged = false

    private val mMaximumVelocity: Int
    private var mCurFlyingType = 0
    // 容器的最大滑动距离
    private var mInnerScrollHeight = 0
    private val mTouchSlop: Int
    private val mScreenWidth: Int
    private var mLastY = 0
    private var mLastMotionY = 0

    private var mChildWebView: NestedScrollingWebView? = null
    private var mChildRecyclerView: RecyclerView? = null

    private var mParentHelper: NestedScrollingParentHelper? = NestedScrollingParentHelper(this)
    private var mScroller: Scroller? = Scroller(getContext())
    private var mVelocityTracker: VelocityTracker? = null

    init {
        val viewConfiguration = ViewConfiguration.get(getContext())
        mMaximumVelocity = viewConfiguration.scaledMaximumFlingVelocity
        mTouchSlop = viewConfiguration.scaledTouchSlop
        mScreenWidth = resources.displayMetrics.widthPixels
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width: Int
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val measureWidth = MeasureSpec.getSize(widthMeasureSpec)

        val measureHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (widthMode == MeasureSpec.EXACTLY) {
            width = measureWidth
        } else {
            width = mScreenWidth
        }

        val left = paddingLeft
        val right = paddingRight
        val top = paddingTop
        val bottom = paddingBottom
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            val params = child.layoutParams
            val childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, left + right, params.width)
            val childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, top + bottom, params.height)
            measureChild(child, childWidthMeasureSpec, childHeightMeasureSpec)
        }
        setMeasuredDimension(width, measureHeight)
        findWebView(this)
        findRecyclerView(this)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var childTotalHeight = 0
        mInnerScrollHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            child.layout(0, childTotalHeight, childWidth, childHeight + childTotalHeight)
            childTotalHeight += childHeight
            mInnerScrollHeight += childHeight
        }
        mInnerScrollHeight -= measuredHeight
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val pointCount = ev.pointerCount
        if (pointCount > 1) {
            return true
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                mIsSetFlying = false
                mIsRvFlyingDown = false
                initOrResetVelocityTracker()
                resetScroller()
                dealWithError()
            }
            MotionEvent.ACTION_MOVE -> {
                initVelocityTrackerIfNotExists()
                mVelocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isParentCenter() && mVelocityTracker != null) {
                //处理连接处的父控件fling事件
                mVelocityTracker?.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                val yVelocity = -mVelocityTracker!!.yVelocity.toInt()
                mCurFlyingType = if (yVelocity > 0) FLYING_FROM_WEBVIEW_TO_PARENT else FLYING_FROM_PARENT_TO_WEBVIEW
                recycleVelocityTracker()
                parentFling(yVelocity.toFloat())
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> mLastY = event.y.toInt()
            MotionEvent.ACTION_MOVE -> {
                if (mLastY == 0) {
                    mLastY = event.y.toInt()
                    return true
                }
                val y = event.y.toInt()
                val dy = y - mLastY
                mLastY = y
                scrollBy(0, -dy)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> mLastY = 0
        }
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_MOVE -> {
                // 拦截落在不可滑动子View的MOVE事件
                val y = ev.y.toInt()
                val yDiff = Math.abs(y - mLastMotionY)
                val isInNestedChildViewArea = isTouchNestedInnerView(ev.rawX.toInt(), ev.rawY.toInt())
                if (yDiff > mTouchSlop && !isInNestedChildViewArea) {
                    mIsBeingDragged = true
                    mLastMotionY = y
                    val parent = parent
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_DOWN -> {
                mLastMotionY = ev.y.toInt()
                mIsBeingDragged = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mIsBeingDragged = false
        }

        return mIsBeingDragged
    }

    fun scrollToTarget(view: View?) {
        if (view == null) {
            return
        }
        mChildWebView?.scrollToBottom()
        scrollTo(0, view.top - 100)
    }

    private fun isTouchNestedInnerView(x: Int, y: Int): Boolean {
        val innerView = mutableListOf<View>()
        mChildWebView?.apply {
            innerView.add(this)
        }
        mChildRecyclerView?.also {
            innerView.add(it)
        }

        for (nestedView in innerView) {
            if (nestedView.visibility != View.VISIBLE) {
                continue
            }
            val location = IntArray(2)
            nestedView.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + nestedView.measuredWidth
            val bottom = top + nestedView.measuredHeight
            if (y in top..bottom && x in left..right) {
                return true
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mScroller?.abortAnimation()
        mScroller = null

        mVelocityTracker = null
        mChildRecyclerView = null
        mChildWebView = null
        mParentHelper = null
    }

    override fun scrollTo(x: Int, py: Int) {
        var y = py
        if (y < 0) {
            y = 0
        }
        if (y > getInnerScrollHeight()) {
            y = getInnerScrollHeight()
        }
        super.scrollTo(x, y)
    }

    override fun computeScroll() {
        mScroller?.apply {
            if (this.computeScrollOffset()) {
                when (mCurFlyingType) {
                    //WebView向父控件滑动
                    FLYING_FROM_WEBVIEW_TO_PARENT -> {
                        if (mIsRvFlyingDown) {
                            //RecyclerView的区域的fling由自己完成
                            return
                        }
                        scrollTo(0, currY)
                        invalidate()
                        checkRvTop()
                        if (scrollY == getInnerScrollHeight() && !mIsSetFlying) {
                            //滚动到父控件底部，滚动事件交给RecyclerView
                            mIsSetFlying = true
                            recyclerViewFling(this.getCurrVelocity().toInt())
                        }
                    }
                    //父控件向WebView滑动
                    FLYING_FROM_PARENT_TO_WEBVIEW -> {
                        scrollTo(0, currY)
                        invalidate()
                        if (currY <= 0 && !mIsSetFlying) {
                            //滚动父控件顶部，滚动事件交给WebView
                            mIsSetFlying = true
                            webViewFling(-this.currVelocity.toInt())
                        }
                    }
                    //RecyclerView向父控件滑动，fling事件，单纯用于计算速度。RecyclerView的flying事件传递最终会转换成Scroll事件处理.
                    FLYING_FROM_RVLIST_TO_PARENT ->
                        if (scrollY != 0) {
                            invalidate()
                        } else if (!mIsSetFlying) {
                            mIsSetFlying = true
                            //滑动到顶部时，滚动事件交给WebView
                            webViewFling((-this.currVelocity).toInt())
                        }
                }
            }
        }
    }

    private fun webViewFling(v: Int) {
        mChildWebView?.flingScroll(0, v)

    }

    private fun recyclerViewFling(v: Int) {
        mChildRecyclerView?.fling(0, v)
    }

    private fun findRecyclerView(parent: ViewGroup) {
        if (mChildRecyclerView != null) {
            return
        }

        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            if (child is RecyclerView && TAG_NESTED_SCROLL_RECYCLERVIEW.equals(child.getTag())) {
                mChildRecyclerView = child
                break
            }
            if (child is ViewGroup) {
                findRecyclerView(child)
            }
        }
    }

    private fun findWebView(parent: ViewGroup) {
        if (mChildWebView != null) {
            return
        }
        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            if (child is NestedScrollingWebView && TAG_NESTED_SCROLL_WEBVIEW == child.getTag()) {
                mChildWebView = child
                break
            }
            if (child is ViewGroup) {
                findWebView(child)
            }
        }
    }

    private fun isParentCenter(): Boolean {
        return scrollY > 0 && scrollY < getInnerScrollHeight()
    }

    private fun scrollToWebViewBottom() {
        mChildWebView?.scrollToBottom()
    }

    private fun initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        } else {
            mVelocityTracker?.clear()
        }
    }

    private fun initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        mVelocityTracker?.recycle()
        mVelocityTracker = null
    }

    private fun resetScroller() {
        mScroller?.apply {
            if (!isFinished) {
                abortAnimation()
            }
        }
        mChildRecyclerView?.stopScroll()
    }

    /**
     * 处理未知的错误情况
     */
    private fun dealWithError() {
        //当父控件有偏移，但是WebView却不在底部时，属于异常情况，进行修复，
        //有两种修复方案：1.将WebView手动滑动到底部，2.将父控件的scroll位置重置为0
        //目前的测试中没有出现这种异常，此代码作为异常防御
        if (isParentCenter() && canWebViewScrollDown()) {
            if (scrollY > measuredHeight / 4) {
                scrollToWebViewBottom()
            } else {
                scrollTo(0, 0)
            }
        }
    }

    private fun parentFling(velocityY: Float) {
        mScroller?.fling(0, scrollY, 0, velocityY.toInt(), 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE)
        invalidate()
    }

    private fun checkRvTop() {
        if (isParentCenter() && !isRvTop()) {
            rvScrollToPosition(0)
        }
    }

    private fun rvScrollToPosition(position: Int) {
        mChildRecyclerView?.apply {
            this.scrollToPosition(position)
            val manager = this.layoutManager
            if (manager is LinearLayoutManager) {
                manager.scrollToPositionWithOffset(position, 0)
            }
        }
    }

    private fun isRvTop(): Boolean {
        return mChildRecyclerView?.let { !it.canScrollVertically(-1) } ?: false
    }

    private fun canWebViewScrollDown(): Boolean {
        return mChildWebView?.canScrollDown() ?: false
    }

    private fun getInnerScrollHeight(): Int {
        return mInnerScrollHeight
    }


    private fun getNestedScrollingHelper(): NestedScrollingParentHelper {
        if (mParentHelper == null) {
            mParentHelper = NestedScrollingParentHelper(this)
        }
        return mParentHelper!!
    }

    /****** NestedScrollingParent2 BEGIN ******/

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun getNestedScrollAxes(): Int {
        return getNestedScrollingHelper().nestedScrollAxes
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        getNestedScrollingHelper().onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        getNestedScrollingHelper().onStopNestedScroll(target, type)
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        if (target is NestedScrollingWebView) {
            //WebView滑到底部时，继续向下滑动父控件和RV
            mCurFlyingType = FLYING_FROM_WEBVIEW_TO_PARENT
            parentFling(velocityY)
        } else if (target is RecyclerView && velocityY < 0 && scrollY >= getInnerScrollHeight()) {
            //RV滑动到顶部时，继续向上滑动父控件和WebView，这里用于计算到达父控件的顶部时RV的速度
            mCurFlyingType = FLYING_FROM_RVLIST_TO_PARENT
            parentFling(velocityY)
        } else if (target is RecyclerView && velocityY > 0) {
            mIsRvFlyingDown = true
        }
        return false
    }

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return false
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        if (dyUnconsumed < 0) {
            //RecyclerView向父控件的滑动衔接处
            scrollBy(0, dyUnconsumed)
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        val isWebViewBottom = !canWebViewScrollDown()
        val isCenter = isParentCenter()
        if (dy > 0 && isWebViewBottom && scrollY < getInnerScrollHeight()) {
            //为了WebView滑动到底部，继续向下滑动父控件
            scrollBy(0, dy)
            if (consumed != null) {
                consumed[1] = dy
            }
        } else if (dy < 0 && isCenter) {
            //为了RecyclerView滑动到顶部时，继续向上滑动父控件
            scrollBy(0, dy)
            if (consumed != null) {
                consumed[1] = dy
            }
        }
        if (isCenter && !isWebViewBottom) {
            //异常情况的处理
            scrollToWebViewBottom()
        }
    }

}