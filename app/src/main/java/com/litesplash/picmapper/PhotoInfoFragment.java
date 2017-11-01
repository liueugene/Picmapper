package com.litesplash.picmapper;

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
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class PhotoInfoFragment extends Fragment implements AnimImageView.Callback {

    public static final String PHOTO_ITEM_TAG = "photoItem";
    public static final String BOTTOM_PADDING_TAG = "bottomPadding";
    
    private static final String LOG_TAG = "PhotoInfoFragment";

    private static final int[] GRADIENT_COLORS = {0xFFF5F5F5, 0xFFEEEEEE};

    private PhotoItem item;

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

    public static PhotoInfoFragment newInstance(PhotoItem photoItem, int bottomPadding) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(PHOTO_ITEM_TAG, photoItem);
        bundle.putInt(BOTTOM_PADDING_TAG, bottomPadding);

        PhotoInfoFragment fragment = new PhotoInfoFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View infoOverlay = inflater.inflate(R.layout.photo_info_panel_layout, container, false);

        item = getArguments().getParcelable("photoItem");
        animDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        int bottomPadding = getArguments().getInt(BOTTOM_PADDING_TAG);
        infoOverlay.setPadding(infoOverlay.getPaddingLeft(), infoOverlay.getPaddingTop(), infoOverlay.getPaddingRight(), infoOverlay.getPaddingBottom() + bottomPadding);

        prepareView(infoOverlay, getArguments());
        return infoOverlay;
    }

    private void prepareView(final View infoOverlayView, Bundle args) {
        imageView = (AnimImageView) infoOverlayView.findViewById(R.id.photo_view);
        imageView.setCallback(this);

        //load image after view is drawn so that it can be resized appropriately
        infoOverlayView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int width, height;

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    width = infoOverlayView.getWidth() - infoOverlayView.getPaddingLeft() - infoOverlayView.getPaddingRight();
                    height = displayMetrics.heightPixels / 2;
                } else {
                    height = infoOverlayView.getHeight() - infoOverlayView.getPaddingTop() - infoOverlayView.getPaddingBottom();
                    width = displayMetrics.widthPixels / 2;
                }

                Log.d(LOG_TAG, "width: " + width + ", height: " + height);

                if (width > 0 && height > 0) {
                    Picasso.with(getActivity())
                            .load(item.getContentUri())
                            .resize(width, height)
                            .onlyScaleDown()
                            .centerInside()
                            .noFade()
                            .into((Target) imageView);
                    infoOverlayView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });

        TextView filename = (TextView) infoOverlayView.findViewById(R.id.filename);
        TextView latData = (TextView) infoOverlayView.findViewById(R.id.latitude_data_text);
        TextView lonData = (TextView) infoOverlayView.findViewById(R.id.longitude_data_text);
        final TextView locText = (TextView) infoOverlayView.findViewById(R.id.location_text);
        locText.setMovementMethod(new ScrollingMovementMethod());
        locText.setOnClickListener(new View.OnClickListener() {
            private boolean scrolledToEnd;

            @Override
            public void onClick(View v) {
                TextView tv = (TextView) v;
                int scrollToPos;
                int endPos = (int) tv.getLayout().getLineRight(0) - tv.getWidth(); //scroll position to reveal rest of text

                if (endPos <= 0)
                    return;

                if (scrolledToEnd) {
                    scrollToPos = 0;
                    scrolledToEnd = false;
                } else {
                    scrollToPos = endPos;
                    scrolledToEnd = true;
                }

                Log.d(LOG_TAG, "scroll to position " + scrollToPos);

                ObjectAnimator scrollAnim = ObjectAnimator.ofInt(tv, "scrollX", scrollToPos);
                scrollAnim.setDuration(200);
                scrollAnim.start();
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

        Log.d(LOG_TAG, "getView(): " + getView().toString());

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


    @Override
    public void onReadyToAnimate(int bitmapWidth, int bitmapHeight) {

        final View fragmentView = getView();

        if (fragmentView == null)
            return;

        Log.d(LOG_TAG, "bitmap height: " + bitmapHeight);
        Log.d(LOG_TAG, "pre-bitmap fragment height: " + fragmentView.getHeight());
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
