package com.android.hq.fastscrollwebview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroupOverlay;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

public class FastScrollerHelper {
    private static final long TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

    private static final int STATE_NONE = 0;
    private static final int STATE_VISIBLE = 1;
    private static final int STATE_DRAGGING = 2;

    private static final long FADE_TIMEOUT = 1000;
    private static final int DURATION_FADE_OUT = 224;
    private static final int DURATION_FADE_IN = 6;
    private static final float VELOCITY_FAST_SCROLL = 6000; //临界速度

    private final FastScrollWebView mWebView;
    private final ViewGroupOverlay mOverlay;

    private final ImageView mThumbImage;
    private AnimatorSet mDecorAnimation;

    private boolean mEnabled = true;
    private boolean mUpdatingLayout;
    private boolean mIsShowing = false;

    private long mPendingDrag = -1;

    private float mInitialTouchY;

    private final int mMiniWidthTouchTarget;
    private final int mMiniHeightTouchTarget;
    private int mState;
    private int mScaledTouchSlop;

    private final Rect mContainerRect = new Rect();
    private final Rect mTempBounds = new Rect();
    private VelocityTracker mVelocityTracker;

    public FastScrollerHelper(FastScrollWebView webView){
        final Context context = webView.getContext();
        mWebView = webView;
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mThumbImage = new ImageView(context);
        //mThumbImage.setScaleType(ImageView.ScaleType.FIT_XY);
        //mThumbImage.setScaleX(0.6f);
        //mThumbImage.setScaleY(0.6f);
//        mThumbImage.setMaxWidth(96);
//        mThumbImage.setMaxHeight(126);

        mThumbImage.setImageResource(R.drawable.scrollbar_drag);
        //mThumbImage.setImageDrawable(mWebView.getContext().getResources().getDrawable(R.drawable.scrollbar_drag, mWebView.getContext().getTheme()));

        mThumbImage.setEnabled(true);
        mThumbImage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        mThumbImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        mOverlay = webView.getOverlay();
        mOverlay.add(mThumbImage);
//        ViewGroup.LayoutParams lp = mThumbImage.getLayoutParams();
//        lp.width = 96;
//        lp.height = 126;
//        mThumbImage.setLayoutParams(lp);

        mMiniWidthTouchTarget = context.getResources().getDimensionPixelOffset(R.dimen.fast_scroller_minimum_width_touch_target);
        mMiniHeightTouchTarget = context.getResources().getDimensionPixelOffset(R.dimen.fast_scroller_minimum_height_touch_target);
        postAutoHide();
    }

    private boolean isEnabled() {
        return mEnabled;
    }

    public void setFastScrollEnable(boolean enable){
        mEnabled = enable;
    }

    public boolean onTouchEvent(MotionEvent me) {
        if (!isEnabled()) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(me);

        switch (me.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                //remove callback
                if(mState == STATE_VISIBLE){
                    setState(STATE_VISIBLE);
                }

                if (mPendingDrag >= 0) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float velocity = mVelocityTracker.getYVelocity();
                    if(Math.abs(velocity) >= VELOCITY_FAST_SCROLL && mState == STATE_NONE
                            && mWebView.getVerticalScrollRange() > 3*mWebView.getHeight()){
                        showFastScroller();
                    }

                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                cancelPendingDrag();
                if(mState == STATE_DRAGGING){
                    setState(STATE_VISIBLE);
                    postAutoHide();
                    return true;
                }else if(mState == STATE_VISIBLE){
                    postAutoHide();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(!mIsShowing){
                    return false;
                }
                if (mPendingDrag >= 0 && Math.abs(me.getY() - mInitialTouchY) > mScaledTouchSlop) {
                    beginDrag();
                }
                if (mState == STATE_DRAGGING) {
                    float position = calculateScrollToPosition(me);
                    position = position < 0f ? 0f : position;
                    scrollTo(position);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                cancelPendingDrag();
                break;
        }

        return false;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mIsShowing && isPointInside(ev.getX(), ev.getY())) {
                    mInitialTouchY = ev.getY();
                    startPendingDrag();
                    return true;
                }
                break;
        }

        return false;
    }

    private void updateLayout() {
        if (!mIsShowing || mUpdatingLayout || !isEnabled()) {
            return;
        }

        mUpdatingLayout = true;

        updateContainerRect();
        layoutThumb();
        Rect r = new Rect();
        mWebView.getDrawingRect(r);
        float percent = calculateScrollProgress();
        float translationY = mWebView.getScrollY()
                + (mWebView.getHeight()-mWebView.getPaddingTop() - mWebView.getPaddingBottom())* percent
                - mThumbImage.getHeight() * percent;
        translationY = translationY < 0f ? 0f : translationY;
        mThumbImage.setTranslationY(translationY);
        mThumbImage.setTranslationX(mWebView.getHorizontalScrollOffset());

        mUpdatingLayout = false;

        if(mState == STATE_VISIBLE){ 
            postAutoHide();
        }
    }


    private void scrollTo(float position) {
        mWebView.scrollTo(mWebView.getScrollX(), (int) position);
    }

    public void setTranslationX(){
        mThumbImage.setTranslationX(mWebView.getHorizontalScrollOffset());
    }

    public void remove() {
        mOverlay.remove(mThumbImage);
    }

    private float getPosFromMotionEvent(float y) {
        final float min = mWebView.getTop();
        final float max = mWebView.getBottom();
        final float offset = min;
        final float range = max - min;

        if (range <= 0) {
            return 0f;
        }

        return constrain((y - offset) / range, 0f, 1f);
    }

    private float constrain(float amount, float low, float high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private void setThumbPos() {
        final Rect container = mContainerRect;
        final int top = container.top;
        final int bottom = container.bottom;

        final View thumbImage = mThumbImage;
        final float min = mWebView.getTop();
        final float max = mWebView.getBottom();
        final float offset = min;
        final float range = max - min;
        thumbImage.setTranslationY(mWebView.getScaleY());

    }

    private void updateContainerRect() {
        final FastScrollWebView webView = mWebView;

        final Rect container = mContainerRect;
        container.left = 0;
        container.top = 0;
        container.right = webView.getWidth();
        container.bottom = webView.getHeight();

        container.left += webView.getPaddingLeft();
        container.top += webView.getPaddingTop();
        container.right -= webView.getPaddingRight();
        container.bottom -= webView.getPaddingBottom();
    }

    private void updateDrawingRect() {
        final FastScrollWebView webView = mWebView;

        final Rect container = mContainerRect;
        container.left = 0;
        container.top = 0;
        container.right = webView.getWidth();
        container.bottom = webView.getHeight();

        container.left += webView.getPaddingLeft();
        container.top += webView.getPaddingTop();
        container.right -= webView.getPaddingRight();
        container.bottom -= webView.getPaddingBottom();
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateLayout();
    }

    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        updateLayout();
    }

    private void cancelPendingDrag() {
        mPendingDrag = -1;
    }

    private void startPendingDrag() {
        mPendingDrag = SystemClock.uptimeMillis() + TAP_TIMEOUT;
    }

    private void beginDrag() {
        mPendingDrag = -1;
        setState(STATE_DRAGGING);
    }

    private void setState(int state) {
        mWebView.removeCallbacks(mDeferHide);

        if (state == mState) {
            return;
        }
        switch (state) {
            case STATE_NONE:
                mIsShowing = false;
                mWebView.setVerticalScrollBarEnabled(true);
                transitionToHidden();
                break;
            case STATE_VISIBLE:
                mIsShowing = true;
                mWebView.setVerticalScrollBarEnabled(false);
                transitionToVisible();
                break;
            case STATE_DRAGGING:
                mIsShowing = true;
                transitionToDragging();
                break;
        }

        mState = state;
    }

    private void postAutoHide() {
        mWebView.removeCallbacks(mDeferHide);
        mWebView.postDelayed(mDeferHide, FADE_TIMEOUT);
    }

    private void showFastScroller(){
        if(!mIsShowing){
            setState(STATE_VISIBLE);
        }
    }

    private final Runnable mDeferHide = new Runnable() {
        @Override
        public void run() {
            setState(STATE_NONE);
        }
    };

    private void applyLayout(View view, Rect bounds) {
        view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    private void measureViewToSide(View view, View adjacent, Rect margins, Rect out) {
        final int marginLeft;
        final int marginTop;
        final int marginRight;
        if (margins == null) {
            marginLeft = 0;
            marginTop = 0;
            marginRight = 0;
        } else {
            marginLeft = margins.left;
            marginTop = margins.top;
            marginRight = margins.right;
        }

        final Rect container = mContainerRect;
        final int containerWidth = container.width();
        final int maxWidth;
        if (adjacent == null) {
            maxWidth = containerWidth;
        } else {
            maxWidth = adjacent.getLeft();
        }

        final int adjMaxWidth = maxWidth - marginLeft - marginRight;
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(adjMaxWidth, View.MeasureSpec.AT_MOST);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthMeasureSpec, heightMeasureSpec);

        final int width = Math.min(adjMaxWidth, view.getMeasuredWidth());
        final int left;
        final int right;
        right = (adjacent == null ? container.right : adjacent.getLeft()) - marginRight;
        left = right - width;

        final int top = marginTop;
        final int bottom = top + view.getMeasuredHeight();
        out.set(left, top, right, bottom);
    }

    private void layoutThumb() {
        final Rect bounds = mTempBounds;
        measureViewToSide(mThumbImage, null, null, bounds);
        applyLayout(mThumbImage, bounds);
    }

    private boolean isPointInside(float x, float y) {
        return isPointInsideX(x) && isPointInsideY(y);
    }

    private boolean isPointInsideX(float x) {
        final float offset = mThumbImage.getTranslationX();
        final float left = mThumbImage.getLeft() + offset;
        final float right = mThumbImage.getRight() + offset;

        final float targetSizeDiff = mMiniWidthTouchTarget - (right - left);
        final float adjust = targetSizeDiff > 0 ? targetSizeDiff : 0;

        return x >= mThumbImage.getLeft() - adjust;
    }

    private boolean isPointInsideY(float y) {
        final float offset = mThumbImage.getTranslationY()-mWebView.getScrollY();
        final float top = offset;
        final float bottom = mThumbImage.getHeight() + offset;

        final float targetSizeDiff = mMiniHeightTouchTarget - (bottom - top);
        final float adjust = targetSizeDiff > 0 ? targetSizeDiff / 2 : 0;

        return y >= top - adjust && y <= bottom + adjust ;
    }

    private float calculateScrollProgress(){
        float percent = mWebView.getScrollY() *1.0f/ (mWebView.getVerticalScrollRange() - mWebView.getHeight());
        //float percent =  ((mWebView.getScrollY()+(mWebView.getHeight()-mWebView.getPaddingBottom()-mWebView.getPaddingTop())*offset) *1.0f/ mWebView.getVerticalScrollRange());
        return percent > 1.0f ? 1.0f : percent;
    }

    private float calculateScrollToPosition(MotionEvent event){
        int height = mWebView.getHeight() - mWebView.getPaddingTop() - mWebView.getPaddingBottom();
        float y = event.getY();
        float progress;
        if(y<0){
            progress =  0f;
        }else if(y > height){
            progress = 1f;
        }else{
            progress =  y / height;
        }

        float position = (mWebView.getVerticalScrollRange() - height)* progress;
        //float percent = (height * 1.0f)/mWebView.getVerticalScrollRange();
        //position = position - height * percent;
        return position;
    }

    private void transitionToHidden() {
        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }

        final Animator fadeOut = ObjectAnimator.ofFloat(mThumbImage, View.ALPHA, 0f);

        final float offset = mThumbImage.getWidth();
        final float translatingX = mThumbImage.getTranslationX();
        final Animator slideOut = ObjectAnimator.ofFloat(mThumbImage, View.TRANSLATION_X, translatingX, translatingX + offset);

        mDecorAnimation = new AnimatorSet();
        mDecorAnimation.setDuration(DURATION_FADE_OUT);
        mDecorAnimation.setInterpolator(new PathInterpolator(0.44f, 0f, 0.34f, 1f));
        mDecorAnimation.playTogether(fadeOut, slideOut);
        mDecorAnimation.start();
    }

    private void transitionToVisible() {
        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }

        final Animator fadeIn = ObjectAnimator.ofFloat(mThumbImage, View.ALPHA, 1f);

        final float offset = mThumbImage.getWidth();
        final float translatingX = mThumbImage.getTranslationX();
        final Animator slideIn = ObjectAnimator.ofFloat(mThumbImage, View.TRANSLATION_X, translatingX+offset, translatingX);

        mDecorAnimation = new AnimatorSet();
        mDecorAnimation.setDuration(DURATION_FADE_IN);
        mDecorAnimation.playTogether(fadeIn, slideIn);
        mDecorAnimation.start();
    }

    private void transitionToDragging(){
        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }
    }

}
