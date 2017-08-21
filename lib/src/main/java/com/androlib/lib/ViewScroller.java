package com.androlib.lib;

import android.content.Context;
import android.support.v4.widget.ScrollerCompat;

/**
 * Created by ybkj on 2017/8/21.
 */

public class ViewScroller {

    private ScrollerCompat mScroller;

    private float mDragRate;
    private boolean mNeedSpringBackX;
    private boolean mNeedSpringBackY;

    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;
    private int mMinX;
    private int mMaxX;
    private int mMinOverX;
    private int mMaxOverX;
    private int mMinY;
    private int mMaxY;
    private int mMinOverY;
    private int mMaxOverY;

    public ViewScroller (Context context) {
        mScroller = ScrollerCompat.create(context);
    }

    public void fling (int startX, int startY,
                       int velocityX, int velocityY,
                       int minX, int maxX, int minOverX, int maxOverX,
                       int minY, int maxY, int minOverY, int maxOverY,
                       float dragRate) {

        mDragRate = dragRate;
        mNeedSpringBackX = mNeedSpringBackY = false;

        mMinX = minX;
        mMaxX = maxX;
        mMinOverX = minX + (int)((minOverX - minX) / dragRate);
        mMaxOverX = maxX + (int)((maxOverX- maxX) / dragRate);
        if (startX < minX) {
            mStartX = Math.max(mMinOverX, minX + (int)((startX - minX) / dragRate));
        } else if (startX > maxX) {
            mStartX = Math.min(mMaxOverX, maxX + (int)((startX - maxX) / dragRate));
        } else {
            mStartX = startX;
        }

        mMinY = minY;
        mMaxY = maxY;
        mMinOverY = minY + (int)((minOverY - minY) / dragRate);
        mMaxOverY = maxY + (int)((maxOverY- maxY) / dragRate);
        if (startY < minY) {
            mStartY = Math.max(mMinOverY, minY + (int)((startY - minY) / dragRate));
        } else if (startY > maxY) {
            mStartY = Math.min(mMaxOverY, maxY + (int)((startY - maxY) / dragRate));
        } else {
            mStartY = startY;
        }

        mScroller.fling(mStartX, mStartY, velocityX, velocityY, mMinOverX, mMaxOverX, mMinOverY, mMaxOverY);

        mScroller.computeScrollOffset();
        mLastX = mScroller.getCurrX();
        mLastY = mScroller.getCurrY();
        final int finalX = mScroller.getFinalX();
        final int finalY = mScroller.getFinalY();
        mNeedSpringBackX = velocityX < 0 && /*finalX > minOverX && */finalX < mMinX
                            || velocityX > 0 && /*finalX < maxOverX &&*/ finalX > mMaxX;
        mNeedSpringBackY = velocityY < 0 && /*finalY > minOverY &&*/ finalY < mMinY
                            || velocityY > 0 && /*finalY < maxOverY &&*/ finalY > mMaxY;
    }

    public boolean computeScrollOffset () {

        boolean result = mScroller.computeScrollOffset();
        if (!result) {
            if (mNeedSpringBackX || mNeedSpringBackY) {
                mNeedSpringBackX = mNeedSpringBackY = false;
                mScroller.springBack(mLastX, mLastY, mMinX, mMaxX, mMinY, mMaxY);
                if (mScroller.computeScrollOffset()) {
                    mLastX = mScroller.getCurrX();
                    mLastY = mScroller.getCurrY();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            mLastX = mScroller.getCurrX();
            mLastY = mScroller.getCurrY();
        }

        return result;
    }

    public int getCurrX () {
        int currX = mScroller.getCurrX();
        if (currX < mMinX) {
            return mMinX + (int)((currX - mMinX) * mDragRate);
        } else if (currX > mMaxX) {
            return mMaxX + (int)((currX - mMaxX) * mDragRate);
        } else {
            return currX;
        }
    }

    public int getCurrY () {
        int currY = mScroller.getCurrY();
        if (currY < mMinY) {
            return mMinY + (int)((currY - mMinY) * mDragRate);
        } else if (currY > mMaxY) {
            return mMaxY + (int)((currY - mMaxY) * mDragRate);
        } else {
            return currY;
        }
    }
}
