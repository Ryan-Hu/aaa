package com.androlib.pulltorefresh;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.widget.ScrollerCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.androlib.lib.ViewScroller;

/**
 * Created by ybkj on 2017/8/17.
 */

public class MainActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ac_main);

//        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.list);
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//        recyclerView.setAdapter(new Adapter(30));

        mScroller = new ViewScroller(this);
        findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mScroller.fling(0, -303,     //start
                        0, -3477,   //velocity
                        0, 0, 0, 0,       //x min & max
                        -90, 0, -308, 0,
                        1f); //over
                mScroller.computeScrollOffset();
                Log.e("TEST", "--------------- y=" + mScroller.getCurrY());
                mHandler.post(mRunnable);
            }
        });
    }

    private ViewScroller mScroller;
    private Handler mHandler = new Handler();
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (mScroller.computeScrollOffset()) {
                Log.e("TEST", "************* y=" + mScroller.getCurrY());
                mHandler.postDelayed(mRunnable, 30);
            }

        }
    };

    private class Adapter extends RecyclerView.Adapter {

        private int mCount;

        public Adapter (int count) {
            mCount = count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.it_text, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ViewHolder)holder).bind(position + 1);
        }

        @Override
        public int getItemCount() {
            return mCount;
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mText;

        public ViewHolder (View itemView) {
            super(itemView);
            mText = (TextView) itemView;
        }

        public void bind (int position) {
            mText.setText(String.valueOf(position));
        }
    }
}
