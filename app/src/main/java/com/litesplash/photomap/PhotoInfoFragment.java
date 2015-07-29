package com.litesplash.photomap;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.content.res.Configuration;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;

public class PhotoInfoFragment extends Fragment implements View.OnTouchListener, AnimImageView.Callback {

    private static final int[] GRADIENT_COLORS = {0xFFF5F5F5, 0xFFEEEEEE};
    private static final String PHOTO_MARKER_TAG = "photoMarker";
    private static final int MAX_PHOTO_SIZE = 360;

    private AnimImageView imageView;
    private File file;

    private int animDuration;

    private float fingerOffset;
    private float initY;

    public PhotoInfoFragment() {
        // Required empty public constructor
    }

    public static PhotoInfoFragment newInstance(PhotoMarker photoMarker) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(PHOTO_MARKER_TAG, photoMarker);

        PhotoInfoFragment fragment = new PhotoInfoFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View infoOverlay = inflater.inflate(R.layout.photo_info_fragment, container, false);
        infoOverlay.setOnTouchListener(this);

        animDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        updateInfo(infoOverlay, getArguments());
        return infoOverlay;
    }

    //public info updater method for an existing fragment
    public void updateInfo(Bundle bundle) {
        updateInfo(getView(), bundle);
    }

    //private updater that can be called by onCreateView, passing in view to inflate as parameter
    private void updateInfo(final View infoOverlayView, Bundle bundle) {

        imageView = (AnimImageView) infoOverlayView.findViewById(R.id.info_imageview);
        imageView.setOnReadyToAnimateListener(this);

        TextView filename = (TextView) infoOverlayView.findViewById(R.id.filename);
        TextView latData = (TextView) infoOverlayView.findViewById(R.id.latitude_data_text);
        TextView lonData = (TextView) infoOverlayView.findViewById(R.id.longitude_data_text);
        final TextView locText = (TextView) infoOverlayView.findViewById(R.id.location_text);

        PhotoMarker pm = bundle.getParcelable("photoMarker");
        file = pm.getFile();

        //load image after view is drawn so that it can be resized appropriately
        infoOverlayView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                //remove listener to prevent unnecessary future calls
                //method call is version dependent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                    infoOverlayView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                else
                    infoOverlayView.getViewTreeObserver().removeGlobalOnLayoutListener(this);


                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int maxPhotoSize = Util.dpToPx(MAX_PHOTO_SIZE, displayMetrics);
                int width, height;

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    int viewWidth = infoOverlayView.getMeasuredWidth() - infoOverlayView.getPaddingLeft() - infoOverlayView.getPaddingRight();
                    width = Math.min(viewWidth, maxPhotoSize);
                    height = displayMetrics.heightPixels / 2;
                } else {
                    int viewHeight = infoOverlayView.getMeasuredHeight() - infoOverlayView.getPaddingTop() - infoOverlayView.getPaddingBottom();
                    width = displayMetrics.widthPixels / 2;
                    height = Math.min(viewHeight, maxPhotoSize);
                }

                Picasso.with(getActivity())
                        .load(file)
                        .resize(width, height)
                        .onlyScaleDown()
                        .centerInside()
                        .noFade()
                        .into((Target) imageView);
            }
        });

        filename.setText(file.getName());
        latData.setText(String.format("%.5f", pm.getLatitude()));
        lonData.setText(String.format("%.5f", pm.getLongitude()));

        new ReverseGeocodeTask(new Geocoder(getActivity()), new ReverseGeocodeTask.Callback() {
            @Override
            public void onLocationFound(String location) {
                locText.setAlpha(0);
                locText.setText(location);
                locText.animate().alpha(1).setDuration(animDuration);
            }
        }).execute(pm.getLatitude(), pm.getLongitude());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        //note: don't use nextAnim as the animation resource is not saved on activity recreation (orientation change)

        if (!enter)
            return AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_down);

        //only add listener to show photo when fragment is entering
        Animator anim = AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_up);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(MapsActivity.TAG, "onAnimationEnd was called");
                imageView.setLayoutReady();
            }
        });

        return anim;
    }

    public boolean onTouch(View v, MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initY = event.getRawY();
                fingerOffset = initY - v.getTop();
                Log.d(MapsActivity.TAG, "finger offset " + fingerOffset);
                break;

            case MotionEvent.ACTION_MOVE:
                float yPos = event.getRawY();
//                Log.d(MapsActivity.TAG, "touch at y coordinate " + yPos);
                if (yPos - initY > 0)
                    v.setY(yPos - fingerOffset);
                break;

            case MotionEvent.ACTION_UP:
                int origPosition = v.getTop();
                ObjectAnimator animator = ObjectAnimator.ofFloat(v, "y", v.getY(), origPosition);
                animator.setDuration(animDuration);
                animator.setInterpolator(new AccelerateDecelerateInterpolator());
                animator.start();
                break;
        }

        return true;
    }

    @Override
    public void onReadyToAnimate(int bitmapWidth, int bitmapHeight) {

        AnimatorSet animSet = new AnimatorSet();
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f);
        fadeAnim.setDuration(animDuration);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            ValueAnimator heightAnim = ValueAnimator.ofInt(0, bitmapHeight);
            heightAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int curHeight = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                    layoutParams.height = curHeight;
                    imageView.setLayoutParams(layoutParams);
                }
            });

            heightAnim.setDuration(animDuration);
            animSet.play(heightAnim).with(fadeAnim);

        } else {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) imageView.getLayoutParams();
            params.leftMargin = Util.dpToPx(10, getResources().getDisplayMetrics());
            imageView.setLayoutParams(params);

            ValueAnimator widthAnim = ValueAnimator.ofInt(0, bitmapWidth);
            widthAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int curImageWidth = (int) animation.getAnimatedValue();

                    ViewGroup.LayoutParams imageLayoutParams = imageView.getLayoutParams();
                    imageLayoutParams.width = curImageWidth;
                    imageView.setLayoutParams(imageLayoutParams);
                }
            });
            widthAnim.setDuration(animDuration);
            animSet.play(widthAnim).with(fadeAnim);
        }

        animSet.start();
    }

}
