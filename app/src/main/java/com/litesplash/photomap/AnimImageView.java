package com.litesplash.photomap;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Created by Eugene on 7/10/2015.
 */
public class AnimImageView extends ImageView implements Target {

    private static int animDuration;
    private int height;
    private boolean bitmapLoaded;
    private boolean fragmentReady;

    public AnimImageView(Context context) {
        super(context);
    }

    public AnimImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {

        setVisibility(GONE);
        setImageBitmap(bitmap);
        height = bitmap.getHeight();
        Log.d(MapsActivity.TAG, "onBitmapLoaded bitmap height: " + height);

        bitmapLoaded = true;

        if (fragmentReady)
            animatePhotoIntoView();
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {

    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {

    }

    public void setFragmentReady() {
        fragmentReady = true;

        if (bitmapLoaded)
            animatePhotoIntoView();
    }

    public void animatePhotoIntoView() {

        setVisibility(VISIBLE);

        animDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        Log.d(MapsActivity.TAG, "animatePhotoIntoView bitmap height: " + height);

        ValueAnimator heightAnim = ValueAnimator.ofInt(0, height);
        heightAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int curHeight = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = curHeight;
                setLayoutParams(layoutParams);
            }
        });

        heightAnim.setDuration(animDuration);

        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f);
        fadeAnim.setDuration(animDuration);

        AnimatorSet animSet = new AnimatorSet();
        animSet.play(heightAnim).with(fadeAnim);
        animSet.start();
    }
}
