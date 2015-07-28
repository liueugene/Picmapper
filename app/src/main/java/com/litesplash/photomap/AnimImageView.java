package com.litesplash.photomap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Created by Eugene on 7/10/2015.
 */
public class AnimImageView extends ImageView implements Target {

    private static int animDuration;
    private int height;
    private int width;
    private boolean bitmapLoaded;
    private boolean layoutReady;

    private OnReadyToAnimateListener listener;

    public AnimImageView(Context context) {
        this(context, null, 0);
    }

    public AnimImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnReadyToAnimateListener(OnReadyToAnimateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {

        setVisibility(GONE);
        setImageBitmap(bitmap);
        height = bitmap.getHeight();
        width = bitmap.getWidth();
        Log.d(MapsActivity.TAG, "onBitmapLoaded bitmap height: " + height);
        Log.d(MapsActivity.TAG, "onBitmapLoaded bitmap width: " + width);

        bitmapLoaded = true;

        if (layoutReady)
            animatePhotoIntoView();
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {

    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {

    }

    public void setLayoutReady() {
        layoutReady = true;

        if (bitmapLoaded)
            animatePhotoIntoView();
    }

    public void animatePhotoIntoView() {
        setVisibility(VISIBLE);

        if (listener != null)
            listener.onReadyToAnimate(width, height);
    }

    public interface OnReadyToAnimateListener {
        void onReadyToAnimate(int bitmapWidth, int bitmapHeight);
    }
}
