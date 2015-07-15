package com.litesplash.photomap;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PhotoInfoFragment extends Fragment implements View.OnTouchListener {

    private static final int[] GRADIENT_COLORS = {0xFFF5F5F5, 0xFFEEEEEE};
    private static final String PHOTO_MARKER_TAG = "photoMarker";

    private AnimImageView imageView;
    private File file;

    private static int animDuration;
    private Context context;
    private float fingerOffset;

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

        context = infoOverlay.getContext();
        animDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        updateInfo(infoOverlay, getArguments());
        return infoOverlay;
    }

    //public info updater method for an existing fragment
    public void updateInfo(Bundle bundle) {
        updateInfo(getView(), bundle);
    }

    //private updater that can be called by onCreateView, passing in view to inflate as parameter
    private void updateInfo(View infoOverlayView, Bundle bundle) {

        imageView = (AnimImageView) infoOverlayView.findViewById(R.id.info_imageview);
        TextView filename = (TextView) infoOverlayView.findViewById(R.id.filename);
        TextView latData = (TextView) infoOverlayView.findViewById(R.id.latitude_data_text);
        TextView lonData = (TextView) infoOverlayView.findViewById(R.id.longitude_data_text);
        TextView locText = (TextView) infoOverlayView.findViewById(R.id.location_text);

        PhotoMarker pm = bundle.getParcelable("photoMarker");
        file = pm.getFile();

        //load image after view is drawn so that it can be resized appropriately
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                //remove listener to prevent unnecessary future calls
                //method call is version dependent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                    imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                else
                    imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                int width = Math.max(imageView.getMeasuredWidth(), 2);
                Log.d(MapsActivity.TAG, "the measured width is " + width);

                Picasso.with(context)
                        .load(file)
                        .resize(width, width)
                        .onlyScaleDown()
                        .centerInside()
                        .noFade()
                        .into((Target) imageView);
            }
        });

        filename.setText(file.getName());
        latData.setText(String.format("%.5f", pm.getLatitude()));
        lonData.setText(String.format("%.5f", pm.getLongitude()));

        new ReverseGeocodeTask(locText).execute(pm.getLatitude(), pm.getLongitude());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {

        final Animator anim = AnimatorInflater.loadAnimator(context, nextAnim);

        if (!enter)
            return anim;

        //only show photo after enter animation
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(MapsActivity.TAG, "onAnimationEnd was called");
                imageView.setFragmentReady();
            }
        });

        return anim;
    }

    public boolean onTouch(View v, MotionEvent event) {

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                float initY = event.getRawY();
                fingerOffset = initY - v.getTop();
                Log.d(MapsActivity.TAG, "finger offset " + fingerOffset);

            case MotionEvent.ACTION_MOVE:
                float yPos = event.getRawY();
//                Log.d(MapsActivity.TAG, "touch at y coordinate " + yPos);
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

    private class ReverseGeocodeTask extends AsyncTask<Double, Void, String> {

        private TextView locText;

        public ReverseGeocodeTask(TextView locText) {
            super();
            this.locText = locText;
        }

        protected String doInBackground(Double... params) {
            double latitude = params[0];
            double longitude = params[1];
            Geocoder geocoder = new Geocoder(context);
            String locationStr = null;

            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses == null || addresses.isEmpty())
                    return "Unknown location";

                Address addr = addresses.get(0);

                String city = addr.getLocality();
                String state = addr.getAdminArea();
                String zip = addr.getPostalCode();

                locationStr = (city == null) ? "" : city + ", ";
                locationStr += (state == null) ? "" : state + " ";
                locationStr += (zip == null) ? "" : zip;

            } catch (IOException e) {
                Log.e(MapsActivity.TAG, "IO exception when reverse geocoding latLon " + latitude + ", " + longitude);
            }

            if(locationStr == null || locationStr.isEmpty())
                locationStr = "Unknown location";

            return locationStr;
        }

        @Override
        protected void onPostExecute(String locationStr) {
            locText.setAlpha(0);
            locText.setText(locationStr);
            locText.animate().alpha(1).setDuration(animDuration);
        }
    }
}
