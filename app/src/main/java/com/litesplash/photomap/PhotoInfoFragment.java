package com.litesplash.photomap;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.res.Configuration;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class PhotoInfoFragment extends Fragment implements View.OnTouchListener, AnimImageView.Callback {

    public static final String PHOTO_ITEM_TAG = "photoItem";

    private static final int[] GRADIENT_COLORS = {0xFFF5F5F5, 0xFFEEEEEE};
    private static final int MAX_PHOTO_SIZE = 360;

    private AnimImageView imageView;
    private ViewGroup parent;

    private int animDuration;
    private boolean animateExit = true;

    private float yOffset;
    private float initY;
    private VelocityTracker velocityTracker;

    private PhotoInfoFragmentListener listener;

    public PhotoInfoFragment() {
        // Required empty public constructor
    }

    public static PhotoInfoFragment newInstance(PhotoItem photoItem) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(PHOTO_ITEM_TAG, photoItem);

        PhotoInfoFragment fragment = new PhotoInfoFragment();
        fragment.setArguments(bundle);
        return fragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View infoOverlay = inflater.inflate(R.layout.photo_info_frag_layout, container, false);
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
        imageView.setOnReadyToAnimateCallback(this);

        TextView filename = (TextView) infoOverlayView.findViewById(R.id.filename);
        TextView latData = (TextView) infoOverlayView.findViewById(R.id.latitude_data_text);
        TextView lonData = (TextView) infoOverlayView.findViewById(R.id.longitude_data_text);
        final TextView locText = (TextView) infoOverlayView.findViewById(R.id.location_text);

        PhotoItem item = bundle.getParcelable("photoItem");
        final String file = "file:" + item.getFilePath();

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

                if (width > 0 && height > 0) {
                    Picasso.with(getActivity())
                            .load(file)
                            .resize(width, height)
                            .onlyScaleDown()
                            .centerInside()
                            .noFade()
                            .into((Target) imageView);
                }
            }
        });

        filename.setText(item.getFilename());
        latData.setText(String.format("%.5f", item.getLatitude()));
        lonData.setText(String.format("%.5f", item.getLongitude()));

        new ReverseGeocodeTask(new Geocoder(getActivity()), new ReverseGeocodeTask.Callback() {
            @Override
            public void onLocationFound(String location) {
                locText.setAlpha(0);
                locText.setText(location);
                locText.animate().alpha(1).setDuration(animDuration);
            }
        }).execute(item.getLatitude(), item.getLongitude());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        //note: don't use nextAnim as the animation resource is not saved on activity recreation (orientation change)

        if (!enter) {
            if (animateExit)
                return AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_down);
            else
                return null;
        }

        //only add listener to show photo when fragment is entering
        Animator anim = AnimatorInflater.loadAnimator(getActivity(), R.animator.slide_up);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageView.setLayoutReady();
            }
        });

        return anim;
    }

    public void shouldAnimateExit(boolean animateExit) {
        this.animateExit = animateExit;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (PhotoInfoFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement PhotoInfoFragmentListener");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Log.d(MainActivity.TAG, "getView(): " + getView().toString());

        //prevent the parent layout from collapsing the height before the exit animation is finished
        parent = (ViewGroup) getView().getParent();
        ViewGroup.LayoutParams params = parent.getLayoutParams();
        params.height = getView().getHeight();
        parent.setLayoutParams(params);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;

        //reset parent layout height to wrap_content
        if (parent != null) {
            ViewGroup.LayoutParams params = parent.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            parent.setLayoutParams(params);
            parent = null;
        }
    }


    public boolean onTouch(View v, MotionEvent event) {
        return false;
/*
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                initY = event.getRawY();
                yOffset = v.getTop() - initY;
//                Log.d(MainActivity.TAG, "finger offset " + yOffset);
                break;

            case MotionEvent.ACTION_MOVE:
                float yPos = event.getRawY();
//                Log.d(MainActivity.TAG, "touch at y coordinate " + yPos);
//                if (yPos - initY > 0)
                v.setY(Math.max(yPos + yOffset, v.getTop()));

                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000);
                break;

            case MotionEvent.ACTION_UP:
                int origPosition = v.getTop();

                float yVelocity = velocityTracker.getYVelocity(event.getPointerId(event.getActionIndex()));
                Log.d(MainActivity.TAG, "y velocity: " + yVelocity);
                velocityTracker.recycle();

                ObjectAnimator animator = ObjectAnimator.ofFloat(v, "y", v.getY(), origPosition);
                animator.setDuration(animDuration);
                animator.setInterpolator(new AccelerateDecelerateInterpolator());
                animator.start();
                break;
        }

        return true;
        */
    }

    @Override
    public void onReadyToAnimate(int bitmapWidth, int bitmapHeight) {

        final View fragmentView = getView();

        if (fragmentView == null)
            return;

        Log.d(MainActivity.TAG, "bitmap height: " + bitmapHeight);
        Log.d(MainActivity.TAG, "pre-bitmap fragment height: " + fragmentView.getHeight());
        listener.onFinalHeightMeasured(bitmapHeight + fragmentView.getHeight());

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
/*
            //panel height hack so that exit animation shows properly
            heightAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ViewGroup panelFrame = (ViewGroup) fragmentView.getParent();
                    ViewGroup.LayoutParams params = panelFrame.getLayoutParams();
                    params.height = fragmentView.getHeight();
                    panelFrame.setLayoutParams(params);
                }
            });
*/
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

    public interface PhotoInfoFragmentListener {
        void onFinalHeightMeasured(int height);
    }
}
