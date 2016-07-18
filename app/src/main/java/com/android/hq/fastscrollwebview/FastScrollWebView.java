package com.android.hq.fastscrollwebview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

public class FastScrollWebView extends WebView {

    private FastScrollerHelper mFastScroller;
    private int mVerticalScrollRange;
    private int mHorizontalScrollOffset;

    public FastScrollWebView(Context context) {
        super(context);
        init();
    }

    public FastScrollWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FastScrollWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public FastScrollWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init(){
        mFastScroller = new FastScrollerHelper(this);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if(mFastScroller != null){
            mFastScroller.onScrollChanged(l, t, oldl, oldt);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mFastScroller != null && mFastScroller.onInterceptTouchEvent(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mFastScroller != null) {
            boolean intercepted = mFastScroller.onTouchEvent(event);
            if (intercepted) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected int computeVerticalScrollRange() {
        mVerticalScrollRange = super.computeVerticalScrollRange();
        return mVerticalScrollRange;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        mHorizontalScrollOffset = super.computeHorizontalScrollOffset();
        mFastScroller.setTranslationX();
        return mHorizontalScrollOffset;
    }

    public int getVerticalScrollRange(){
        return mVerticalScrollRange;
    }

    public int getHorizontalScrollOffset(){
        return mHorizontalScrollOffset;
    }
}
