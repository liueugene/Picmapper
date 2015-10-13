package com.litesplash.picmapper;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Created by Eugene on 7/8/2015.
 */
public class SlidingFrameLayout extends FrameLayout {

    public SlidingFrameLayout(Context context) {
        super(context);
    }

    public SlidingFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlidingFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlidingFrameLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setYFraction(final float fraction) {
        setTranslationY((getHeight() == 0) ? Float.MAX_VALUE : getHeight() * fraction);
    }

    public float getYFraction() {
        return (getHeight() == 0) ? 0 : getTranslationY() / getHeight();
    }
}
