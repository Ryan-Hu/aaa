package com.androlib.lib;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Created by ybkj on 2017/8/20.
 */

public class NestedRecyclerView extends RecyclerView {

    public NestedRecyclerView (Context context) {
        super(context);
    }

    public NestedRecyclerView (Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onScrolled(int dx, int dy) {
        Log.e("TEST", "********* " + canScrollVertically(-1));
    }
}
