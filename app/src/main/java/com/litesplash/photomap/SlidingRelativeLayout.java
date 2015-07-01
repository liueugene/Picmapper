package com.litesplash.photomap;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class SlidingRelativeLayout extends RelativeLayout {

    public SlidingRelativeLayout(Context context) {
        super(context);
    }

    public SlidingRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlidingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setYFraction(final float fraction) {
        setTranslationY((getHeight() == 0) ? Float.MAX_VALUE : getHeight() * fraction);
    }

    public float getYFraction() {
        return (getHeight() == 0) ? 0 : getTranslationY() / getHeight();
    }
}
