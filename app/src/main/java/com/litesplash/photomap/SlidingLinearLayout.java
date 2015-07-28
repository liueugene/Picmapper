package com.litesplash.photomap;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * Created by Eugene on 7/23/2015.
 */
public class SlidingLinearLayout extends LinearLayout {

    public SlidingLinearLayout(Context context) {
        super(context);
    }

    public SlidingLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlidingLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlidingLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setYFraction(final float fraction) {
        setTranslationY((getHeight() == 0) ? Float.MAX_VALUE : getHeight() * fraction);
    }

    public float getYFraction() {
        return (getHeight() == 0) ? 0 : getTranslationY() / getHeight();
    }
}
