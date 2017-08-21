package com.androlib.lib;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ScrollerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * Created by ybkj on 2017/8/15.
 */

public class PullRefreshLayout extends FrameLayout implements NestedScrollingParent, NestedScrollingChild {

    private static final String TAG = PullRefreshLayout.class.getSimpleName();

    public static final int TOP = 1;
    public static final int BOTTOM = 2;

    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = .35f;
    private static final int FLING_OVER_DISTANCE_DIP = 20;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float DEFAULT_DISTANCE_TO_TRIGGER_REFRESH_DIP = 60;

    private View mRefreshTarget;
    private View mTopProgress;
    private View mBottomProgress;

    private RefreshStateListener mTopStateListener;
    private RefreshStateListener mBottomStateListener;
//    private boolean mRefreshingPending;     //refresh triggered when in drag state， have to wait until idle state
    private boolean mRefreshFinishing;      //refresh is finished while in drag state,can not pull to refresh until next drag
    private boolean mTopRefreshing ;
    private boolean mBottomRefreshing;
    private boolean mTopRefreshEnabled = true;
    private boolean mBottomRefreshEnabled = true;
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingOverDistance;
    private int mDefaultRefreshDistance;    //default distance to pull to trigger refresh
    private int mTopRefreshDistance;
    private int mBottomRefreshDistance;
    private int mTopMaxPullDistance;
    private int mBottomMaxPullDistance;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];
    private int mNestedYOffset;
    private boolean mNestedScrollInProgress;

    private float mInitialMotionDown;
    private int mLastMotionY;
    private boolean mIsBeingDragged;
    private int mActivePointerId = INVALID_POINTER;
    private VelocityTracker mVelocityTracker;
    private ScrollerCompat mScroller;
    private ViewScroller mViewScroller;


    public PullRefreshLayout (Context context) {
        this(context, null);
    }

    public PullRefreshLayout (Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mFlingOverDistance = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, FLING_OVER_DISTANCE_DIP, context.getResources().getDisplayMetrics());
        mDefaultRefreshDistance = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_DISTANCE_TO_TRIGGER_REFRESH_DIP, getContext().getResources().getDisplayMetrics());
        mScroller = ScrollerCompat.create(getContext(), null);
        mViewScroller = new ViewScroller(getContext());
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    public void setTopMaxPullDistance (int distance) {
        mTopMaxPullDistance = distance;
    }

    public void setBottomMaxPullDistance (int distance) {
        mBottomMaxPullDistance = distance;
    }

    public void setTopRefreshDistance (int distance) {
        mTopRefreshDistance = distance;
    }

    public void setBottomRefreshDistance (int distance) {
        mBottomRefreshDistance = distance;
    }

    public void addRefreshTarget (View view) {
        LayoutParams lp = (LayoutParams)generateDefaultLayoutParams();
        lp.refreshTarget = true;
        addView(view, lp);
    }

    public void addProgressTarget (View view, int position) {
        LayoutParams lp = (LayoutParams)generateDefaultLayoutParams();
        lp.progressTarget = true;
        addView(view, lp);
    }

    private void ensureTargets () {
        mRefreshTarget = findRefreshTarget();
        mTopProgress = findTopProgress();
        mBottomProgress = findBottomProgress();
    }

    private View findRefreshTarget () {
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.refreshTarget) {
                return child;
            }
        }
        return null;
    }

    private View findTopProgress () {
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.progressTarget && lp.progressPosition == LayoutParams.ProgressPosition.TOP) {
                return child;
            }
        }
        return null;
    }

    private View findBottomProgress () {
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.progressTarget && lp.progressPosition == LayoutParams.ProgressPosition.BOTTOM) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        ensureTargets();
        if (mRefreshTarget != null) {
            mRefreshTarget.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        }
        if (mTopProgress != null) {
            LayoutParams lp = (LayoutParams)mTopProgress.getLayoutParams();
            mTopProgress.measure(getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), getPaddingLeft() + getPaddingRight(), lp.width),
                    getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY), getPaddingTop() + getPaddingBottom(), lp.height));
        }
        if (mBottomProgress != null) {
            LayoutParams lp = (LayoutParams)mBottomProgress.getLayoutParams();
            mBottomProgress.measure(getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), getPaddingLeft() + getPaddingRight(), lp.width),
                    getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY), getPaddingTop() + getPaddingBottom(), lp.height));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mRefreshTarget != null) {
            mRefreshTarget.layout(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        }

        if (mTopProgress != null) {
            int width = mTopProgress.getMeasuredWidth();
            int height = mTopProgress.getMeasuredHeight();
            int bottom = getPaddingTop();
            int top = bottom - height;
            int centerHorizontal = (getPaddingLeft() + getMeasuredWidth() - getPaddingRight()) / 2;
            int left = centerHorizontal - width / 2;
            int right = left + width;
            mTopProgress.layout(left, top, right, bottom);
        }

        if (mBottomProgress != null) {
            int width = mBottomProgress.getMeasuredWidth();
            int height = mBottomProgress.getMeasuredHeight();
            int top = getMeasuredHeight() - getPaddingBottom();
            int bottom = top + height;
            int centerHorizontal = (getPaddingLeft() + getMeasuredWidth() - getPaddingRight()) / 2;
            int left = centerHorizontal - width / 2;
            int right = left + width;
            mBottomProgress.layout(left, top, right, bottom);
        }

//        if (mMaxPullDistance == 0) {
//            mMaxPullDistance = (b - t - getPaddingTop() - getPaddingBottom()) / 2;
//        }
    }

    private boolean canRefreshTargetScrollUp () {
        return mRefreshTarget != null && ViewCompat.canScrollVertically(mRefreshTarget, -1);
    }

    private boolean canRefreshTargetScrollDown () {
        return mRefreshTarget != null && ViewCompat.canScrollVertically(mRefreshTarget, 1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (!isEnabled() || mRefreshTarget == null || mNestedScrollInProgress) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }

        switch (action) {

            case MotionEvent.ACTION_DOWN:

                mIsBeingDragged = false;
                mActivePointerId = ev.getPointerId(0);
                mInitialMotionDown = ev.getY();
                mLastMotionY = (int)mInitialMotionDown;
                mScroller.computeScrollOffset();
                if (!mScroller.isFinished()) {
                    dragStart();
                }
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:

                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }

                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                int y = (int)ev.getY(pointerIndex);
                mLastMotionY = y;
                if (!mIsBeingDragged) {
                    if (mLastMotionY - mInitialMotionDown > mTouchSlop && !canRefreshTargetScrollUp()
                            || mLastMotionY - mInitialMotionDown < -mTouchSlop && !canRefreshTargetScrollDown()) {

                        dragStart();
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                mActivePointerId = INVALID_POINTER;
                if (mScroller.springBack(getScrollX(), getScrollY(), 0, getMinScrollY(), 0, getMaxScrollY())) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                dragEnd();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isEnabled() || mRefreshTarget == null || mNestedScrollInProgress) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(event);

        if (action == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0;
        }

        MotionEvent adjustedEvent = MotionEvent.obtain(event);
        adjustedEvent.offsetLocation(0, mNestedYOffset);

        switch (action) {
            case MotionEvent.ACTION_DOWN:

                mActivePointerId = event.getPointerId(0);
                mInitialMotionDown = event.getY();
                mLastMotionY = (int)mInitialMotionDown;
                mScroller.computeScrollOffset();
                if (!mScroller.isFinished()) {
                    dragStart();
                }
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(event);
                break;

            case MotionEvent.ACTION_MOVE:

                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }

                final int pointerIndex = event.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                int y = (int)event.getY(pointerIndex);
                int deltaY = mLastMotionY - y;
                mLastMotionY = y;

                if (mIsBeingDragged) {
                    if (dispatchNestedPreScroll(0, deltaY, mParentScrollConsumed, mParentOffsetInWindow)) {
                        deltaY -= mParentScrollConsumed[1];
                        adjustedEvent.offsetLocation(0, mParentOffsetInWindow[1]);
                        mNestedYOffset += mParentOffsetInWindow[1];
                        mLastMotionY -= mParentOffsetInWindow[1];
                    }

                    final int oldY = getScrollY();
                    int unconsumedY = overScrollYBy(deltaY, getScrollY(), getMinScrollY(), getMaxScrollY(), getMinOverScrollY(), getMaxOverScrollY(), DRAG_RATE, false);

                    if (dispatchNestedScroll(0, getScrollY() - oldY, 0, unconsumedY, mParentOffsetInWindow)) {
                        adjustedEvent.offsetLocation(0, mParentOffsetInWindow[1]);
                        mNestedYOffset += mParentOffsetInWindow[1];
                        mLastMotionY -= mParentOffsetInWindow[1];
                    }
                }

                if (!mIsBeingDragged) {
                    if (mLastMotionY - mInitialMotionDown > mTouchSlop && !canRefreshTargetScrollUp()
                            || mLastMotionY - mInitialMotionDown < -mTouchSlop && !canRefreshTargetScrollDown()) {
                        dragStart();
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(event);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:

                if (mIsBeingDragged) {
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId);
                    boolean isEdge = initialVelocity > 0 && getScrollY() <= getMinOverScrollY() || initialVelocity < 0 && getScrollY() >= getMaxOverScrollY();
                    if (!isEdge && (Math.abs(initialVelocity) > mMinimumVelocity)) {
                        flingWithNestedDispatch(-initialVelocity);
                    } else {
                        triggerRefreshIfNeeded(1);
                        if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, getMinScrollY(), getMaxScrollY())) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }
                }
                mActivePointerId = INVALID_POINTER;
                dragEnd();
                break;

            case MotionEvent.ACTION_CANCEL:

                if (mIsBeingDragged) {
                    if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, getMinScrollY(), getMaxScrollY())) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                }
                mActivePointerId = INVALID_POINTER;
                dragEnd();
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                //TODO Switch pointer
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(event);
        }
        adjustedEvent.recycle();

        return true;
    }


    private int overScrollYBy (int deltaY, int scrollY, int minScrollYRange, int maxScrollYRange, int minOverScrollY, int maxOverScrollY, float overScrollRate, boolean inRange) {

        if (deltaY == 0) {
            return 0;
        }

        int newScrollY = scrollY;
        int unconsumedY = deltaY;

        if (deltaY > 0) {  //Scroll down

            if (scrollY < maxScrollYRange) {
                if (unconsumedY <= maxScrollYRange - scrollY) {
                    newScrollY += unconsumedY;
                    unconsumedY = 0;
                } else {
                    newScrollY = maxScrollYRange;
                    unconsumedY -= maxScrollYRange - scrollY;
                }
            }

            if (!inRange && unconsumedY > 0 && newScrollY < maxOverScrollY) {
                if (unconsumedY * overScrollRate <= maxOverScrollY - newScrollY) {
                    newScrollY += unconsumedY * overScrollRate;
                    unconsumedY = 0;
                } else {
                    newScrollY = maxOverScrollY;
                    unconsumedY -= (maxOverScrollY - newScrollY) / overScrollRate;
                }
            }

        } else {    //Scroll up


            if (scrollY > minScrollYRange) {
                if (unconsumedY >= minScrollYRange - scrollY) {
                    newScrollY += unconsumedY;
                    unconsumedY = 0;
                } else {
                    newScrollY = minScrollYRange;
                    unconsumedY -= minScrollYRange - scrollY;
                }
            }

            if (!inRange && unconsumedY < 0 && newScrollY > minOverScrollY) {
                if (unconsumedY * overScrollRate >= minOverScrollY - newScrollY) {
                    newScrollY += unconsumedY * overScrollRate;
                    unconsumedY = 0;
                } else {
                    newScrollY = minOverScrollY;
                    unconsumedY -= (minOverScrollY - newScrollY) / overScrollRate;
                }
            }

        }

        scrollTo(getScrollX(), newScrollY);

        return unconsumedY;
    }

    private void flingWithNestedDispatch(int velocityY) {
        if (!dispatchNestedPreFling(0, velocityY)) {
            dispatchNestedFling(0, velocityY, true);
            fling2(velocityY);
        }
    }

    public void fling2 (int velocityY) {

        mScroller.fling(getScrollX(), getScrollY(),     //start
                0, velocityY,   //velocity
                0, 0,       //x min & max
                getMinOverScrollY(), getMaxOverScrollY(),    //y min & max
                0, 0); //over

        ViewCompat.postInvalidateOnAnimation(this);
    }

    public void fling(int velocityY) {

        int overScrollY = velocityY < 0 ?
                Math.max(getMinOverScrollY(), Math.min(getMinScrollY(), getScrollY()) - mFlingOverDistance) - getMinScrollY() :
                Math.min(getMaxOverScrollY(), Math.max(getMaxScrollY(), getScrollY()) + mFlingOverDistance) - getMaxScrollY();

        mScroller.fling(getScrollX(), getScrollY(),     //start
                0, velocityY,   //velocity
                0, 0,       //x min & max
                getMinScrollY(), getMaxScrollY(),    //y min & max
                0, Math.abs(overScrollY)); //over

        Log.e("TEST", "fling velocity=" + velocityY + ", scrollY=" + getScrollY() + ", minY=" + getMinScrollY() + ", maxY=" + getMaxScrollY() + ", overY=" + Math.abs(overScrollY));

        ViewCompat.postInvalidateOnAnimation(this);
    }

    protected void dragStart () {
        mIsBeingDragged = true;
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL);
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    protected void dragEnd () {
        mIsBeingDragged = false;
        recycleVelocityTracker();
        stopNestedScroll();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    private int computeTopRefreshDistance () {
        if (mTopRefreshDistance > 0) {
            return mTopRefreshDistance;
        } else if (mTopProgress != null) {
            return mTopProgress.getHeight();
        } else {
            return mDefaultRefreshDistance;
        }
    }

    private int computeBottomRefreshDistance () {
        if (mBottomRefreshDistance > 0) {
            return mBottomMaxPullDistance;
        } else if (mBottomProgress != null) {
            return mBottomProgress.getHeight();
        } else {
            return mDefaultRefreshDistance;
        }
    }

    private int getMaxScrollY () {
        return mBottomRefreshing ? computeBottomRefreshDistance() : 0;
    }

    private int getMaxOverScrollY () {
        int maxDistance = mBottomMaxPullDistance > 0 ? mBottomMaxPullDistance : (getHeight() - getPaddingTop() - getPaddingBottom()) / 3;
        return Math.max(maxDistance, getMaxScrollY());
    }

    private int getMinScrollY () {
        return mTopRefreshing ? -computeTopRefreshDistance() : 0;
    }

    private int getMinOverScrollY () {
        int maxDistance = mTopMaxPullDistance > 0 ? mTopMaxPullDistance : (getHeight() - getPaddingTop() - getPaddingBottom()) / 3;
        return Math.min(-maxDistance, getMinScrollY());
    }

    @Override
    public void computeScroll() {

        if (mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();


            if (oldX != x || oldY != y) {
                scrollTo(x, y);
            }

            if (!mIsBeingDragged && !mNestedScrollInProgress && !isRefreshing(TOP) && !isRefreshing(BOTTOM)) {
                if (triggerRefreshIfNeeded(2)) {
                    int velocityY = (int)mScroller.getCurrVelocity();
                    fling(y - oldY > 0 ? velocityY : -velocityY);  //re-fling cause final y is changed
                }
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    //********************* NESTED SCROLL ************************

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }


    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return isEnabled()
                && !mIsBeingDragged
                && !mNestedScrollInProgress
                && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
        startNestedScroll(nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mNestedScrollInProgress = true;
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        stopNestedScroll();
        triggerRefreshIfNeeded(3);
        if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, getMinScrollY(), getMaxScrollY())) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
        mNestedScrollInProgress = false;
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        dispatchNestedPreScroll(dx, dy, consumed, null);
        int dyUnconsumed = dy - consumed[1];
        if (dyUnconsumed < 0 && getScrollY() > (getHeight() - getPaddingTop() - getPaddingBottom()) ||
                dyUnconsumed > 0 && getScrollY() < 0) {
            int unconsumedY = overScrollYBy(dyUnconsumed, getScrollY(), getMinScrollY(), getMaxScrollY(), getMinOverScrollY(), getMaxOverScrollY(), DRAG_RATE, true);
            consumed[1] = dyUnconsumed - unconsumedY;
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        int unconsumedY = overScrollYBy(dyUnconsumed, getScrollY(), getMinScrollY(), getMaxScrollY(), getMinOverScrollY(), getMaxOverScrollY(), DRAG_RATE, false);
        dispatchNestedScroll(0, dyUnconsumed - unconsumedY, 0, unconsumedY, null);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        if (!consumed) {
            flingWithNestedDispatch((int) velocityY);
            return true;
        }
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }



    //*********************  REFRESH  ************************

    private boolean triggerRefreshIfNeeded (int source) {
        if (getScrollY() < -computeTopRefreshDistance() && setRefreshStart(TOP, false)) {
            //Top refresh is triggered
            Log.e("TEST", "Triggered from " + source);
            return true;
        } else if (getScrollY() > computeBottomRefreshDistance() && setRefreshStart(BOTTOM, false)) {
            //Bottom refresh is triggered
            return true;
        }
        return false;
    }

    public boolean isRefreshEnabled (int position) {
        switch (position) {
            case TOP:
                return mTopRefreshEnabled;
            case BOTTOM:
                return mBottomRefreshEnabled;
            default:
                return false;
        }
    }

    public void setRefreshEnabled (int position, boolean enabled) {
        if (position == TOP && mTopRefreshEnabled != enabled) {
            mTopRefreshEnabled = enabled;
            if (!mTopRefreshEnabled && isRefreshing(TOP)) {
                setRefreshCanceled(TOP, false);
            }
        } else if (position == BOTTOM && mBottomRefreshEnabled != enabled) {
            mBottomRefreshEnabled = enabled;
            if (!mBottomRefreshEnabled && isRefreshing(BOTTOM)) {
                setRefreshCanceled(BOTTOM, false);
            }
        }
    }

    public boolean setRefreshStart (int position, boolean force) {
        if (position == TOP && !mTopRefreshing) {
            mTopRefreshing = true;
            if (mTopStateListener != null) {
                mTopStateListener.onRefreshStart();
            }
            return true;
        }
        if (position == BOTTOM && !mBottomRefreshing) {
            mBottomRefreshing = true;
            if (mBottomStateListener != null) {
                mBottomStateListener.onRefreshStart();
            }
            return true;
        }
        return false;
    }

    public boolean setRefreshError (int position, Throwable cause, boolean force) {
        if (position == TOP && mTopRefreshing) {
            mTopRefreshing = false;
            if (mTopStateListener != null) {
                mTopStateListener.onRefreshError(cause);
            }
            return true;
        }
        if (position == BOTTOM && mBottomRefreshing) {
            mBottomRefreshing = false;
            if (mBottomStateListener != null) {
                mBottomStateListener.onRefreshError(cause);
            }
            return true;
        }
        return false;
    }

    public boolean setRefreshCanceled (int position, boolean force) {
        if (position == TOP && mTopRefreshing) {
            mTopRefreshing = false;
            if (mTopStateListener != null) {
                mTopStateListener.onRefreshCanceled();
            }
            return true;
        }
        if (position == BOTTOM && mBottomRefreshing) {
            mBottomRefreshing = false;
            if (mBottomStateListener != null) {
                mBottomStateListener.onRefreshCanceled();
            }
            return true;
        }
        return false;
    }


    public boolean isRefreshing (int position) {
        switch (position){
            case TOP:
                return mTopRefreshing;
            case BOTTOM:
                return mBottomRefreshing;
            default:
                return false;
        }
    }

    public void setRefreshStateListener (int position, RefreshStateListener listener) {
        if (position == TOP) {
            mTopStateListener = listener;
        }

    }

    public interface RefreshStateListener {
        void onPullProgress (int progress);
        void onRefreshStart();
        void onRefreshSuccess();
        void onRefreshError(Throwable cause);
        void onRefreshCanceled();
    }

    //*********************  LAYOUT PARAMS ***********************

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected FrameLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public FrameLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams)lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams)lp);
        }
        return new LayoutParams(lp);
    }

    /**
     * LayoutParams for PullRefreshLayout
     */
    public static class LayoutParams extends FrameLayout.LayoutParams {

        public boolean refreshTarget;
        public boolean progressTarget;
        public ProgressPosition progressPosition;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.PullRefreshLayout_Layout);
            refreshTarget = a.getBoolean(R.styleable.PullRefreshLayout_Layout_layout_refreshTarget, false);
            progressTarget = a.getBoolean(R.styleable.PullRefreshLayout_Layout_layout_progressTarget, false);
            progressPosition = ProgressPosition.parse(a.getInt(R.styleable.PullRefreshLayout_Layout_layout_progressPosition, 0));
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams (ViewGroup.LayoutParams lp) {
            super(lp);
        }

        public LayoutParams (ViewGroup.MarginLayoutParams lp) {
            super(lp);
        }

        public LayoutParams (LayoutParams lp) {
            super((ViewGroup.MarginLayoutParams)lp);
            gravity = lp.gravity;
            refreshTarget = lp.refreshTarget;
            progressTarget = lp.progressTarget;
        }

        public enum ProgressPosition {

            TOP(1),
            BOTTOM(2);

            private int mValue;

            ProgressPosition (int value) {
                mValue = value;
            }

            public static ProgressPosition parse (int value) {
                switch (value) {
                    case 2:
                        return BOTTOM;
                    case 1:
                    default:
                        return TOP;
                }
            }
        }
    }
}
