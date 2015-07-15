package com.litesplash.photomap;

import android.content.Context;
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

    public void setYFraction(final float fraction) {
        setTranslationY((getHeight() == 0) ? Float.MAX_VALUE : getHeight() * fraction);
    }

    public float getYFraction() {
        return (getHeight() == 0) ? 0 : getTranslationY() / getHeight();
    }
}
