package com.lws.nestedscrolldemo

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.Scroller
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

class NestedScrollingWebView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, defAttrStyle: Int = 0) :
    WebView(context, attributeSet, defAttrStyle), NestedScrollingChild2 {

    private var mIsSelfFling: Boolean = false
    private var mHasFling: Boolean = false

    private val mTouchSlop: Int
    private var mMaximumVelocity: Int
    private var mFirstY: Int = 0
    private var mLastY: Int = 0
    private var mMaxScrollY: Int = 0
    private var mWebViewContentHeight = 0
    private val mScrollConsumed = intArrayOf(0, 0)
    private val mDensity: Float

    private val mChildHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private var mParentView: NestedScrollingDetailContainer? = null
    private val mScroller: Scroller
    private var mVelocityTracker: VelocityTracker? = null

    init {
        isNestedScrollingEnabled = true
        mScroller = Scroller(getContext())
        val configuration = ViewConfiguration.get(getContext())
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity
        mTouchSlop = configuration.scaledTouchSlop
        mDensity = context.resources.displayMetrics.density
    }

    private fun getWebViewContentHeight(): Int {
        if (mWebViewContentHeight == 0) {
            mWebViewContentHeight = (contentHeight * mDensity).toInt()
        }
        return mWebViewContentHeight
    }

    fun canScrollDown(): Boolean {
        val range = getWebViewContentHeight() - height
        if (range <= 0) {
            return false
        }
        val offset = scrollY
        return offset < range - mTouchSlop
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mWebViewContentHeight = 0
                mLastY = event.rawY.toInt()
                mFirstY = mLastY
                if (!mScroller.isFinished) {
                    mScroller.abortAnimation()
                }
                initOrResetVelocityTracker()
                mIsSelfFling = false
                mHasFling = false
                mMaxScrollY = getWebViewContentHeight() - height
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                initVelocityTrackerIfNotExists()
                mVelocityTracker?.addMovement(event)
                val y = event.rawY.toInt()
                val dy = y - mLastY
                mLastY = y
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!dispatchNestedPreScroll(0, -dy, mScrollConsumed, null)) {
                    scrollBy(0, -dy)
                }
                if (Math.abs(mFirstY - y) > mTouchSlop) {
                    //屏蔽WebView本身的滑动，滑动事件自己处理
                    event.action = MotionEvent.ACTION_CANCEL
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (isParentResetScroll() && mVelocityTracker != null) {
                    mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    val yVelocity = -mVelocityTracker!!.yVelocity.toInt()
                    recycleVelocityTracker()
                    mIsSelfFling = true
                    flingScroll(0, yVelocity)
                }
            }
        }
        super.onTouchEvent(event)
        return true
    }

    override fun flingScroll(vx: Int, vy: Int) {
        mScroller.fling(0, scrollY, 0, vy, 0, 0, Int.MIN_VALUE, Int.MAX_VALUE)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recycleVelocityTracker()
        stopScroll()
        mParentView = null
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            val currY = mScroller.currY
            if (!mIsSelfFling) {
                // parent flying
                scrollTo(0, currY)
                invalidate()
                return
            }

            if (isWebViewCanScroll()) {
                scrollTo(0, currY)
                invalidate()
            }

            if (!mHasFling && mScroller.startY < currY && !canScrollDown()
                && startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                && !dispatchNestedPreFling(0f, mScroller.currVelocity)
            ) {
                //滑动到底部时，将fling传递给父控件和RecyclerView
                mHasFling = true
                dispatchNestedFling(0f, mScroller.currVelocity, false)
            }
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        var py = if (y < 0) 0 else y
        if (mMaxScrollY != 0 && py > mMaxScrollY) {
            py = mMaxScrollY
        }
        if (isParentResetScroll()) {
            super.scrollTo(x, py)
        }
    }

    fun scrollToBottom() {
        super.scrollTo(0, getWebViewContentHeight() - height)
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

    private fun initWebViewParent() {
        if (mParentView != null) {
            return
        }
        var parent = parent as View?
        while (parent != null) {
            if (parent is NestedScrollingDetailContainer) {
                mParentView = parent
                break
            } else {
                parent = parent.parent as View?
            }
        }
    }

    private fun isParentResetScroll(): Boolean {
        mParentView ?: initWebViewParent()
        return mParentView?.scrollY == 0
    }

    private fun stopScroll() {
        if (!mScroller.isFinished) {
            mScroller.abortAnimation()
        }
    }

    private fun isWebViewCanScroll(): Boolean {
        return getWebViewContentHeight() > height
    }


    /****** NestedScrollingChild BEGIN ******/

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        mChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return mChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return mChildHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        mChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mChildHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int): Boolean {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }
}