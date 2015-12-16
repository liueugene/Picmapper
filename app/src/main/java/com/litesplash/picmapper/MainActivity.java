package com.litesplash.picmapper;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.StringRes;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements BaseMapFragment.Listener, PhotoGridFragment.Listener,
        PhotoInfoFragment.PhotoInfoFragmentListener, SlideUpPanelLayout.Listener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = "MainActivity";

    private static final String MAP_FRAGMENT_TAG = "mapFragment";
    private static final String ACTIVE_ITEM_KEY = "activeItem";
    private static final int RESET_EXIT_STATE = 0x2E5E7;
    private static final int COARSE_LOCATION = 0;

    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    private MenuItem searchMenuItem;
    private AutoCompleteTextView searchView;
    private SlideUpPanelLayout mapLayout;

    private RelativeLayout progressLayout;
    private ProgressBar progressBar;

    private AdView adView;

    private BaseMapFragment mapFragment;
    private PhotoInfoFragment photoInfoFragment;

    private GoogleMap gMap;
    private int mapPaddingLeft;
    private int mapPaddingTop;
    private int mapPaddingRight;
    private int mapPaddingBottom;

    private GoogleApiClient googleApiClient;

    private PlacesSuggestionAdapter searchAdapter;

    private PhotoItem activeItem;

    private int toolbarHeight;
    private int statusBarHeight;
    private float lastZoom;
    private boolean gridFragmentShowing;
    private boolean toolbarHidden;

    private boolean firstLaunch;
    private boolean readyToExit = false;
    private Toast exitToast;

    private final MyHandler handler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private WeakReference<MainActivity> activityRef;

        public MyHandler(MainActivity activity) {
            activityRef = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity a = activityRef.get();
            if (a != null) {
                Log.d(LOG_TAG, "readyToExit set to false");
                a.readyToExit = false;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "MainActivity onCreate");
        setContentView(R.layout.activity_maps);
        FragmentManager fm = getFragmentManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId != 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resId);
            }
        }

        exitToast = Toast.makeText(this, "Press back again to exit.", Toast.LENGTH_SHORT);
        mapLayout = (SlideUpPanelLayout) findViewById(R.id.map_layout);
        mapLayout.setFragmentManager(fm);

        //initialize map
        mapFragment = (BaseMapFragment) fm.findFragmentByTag(MAP_FRAGMENT_TAG);

        if (mapFragment == null) {
            mapFragment = BaseMapFragment.newInstance();
            fm.beginTransaction().add(mapFragment, MAP_FRAGMENT_TAG).commit();
        }

        mapLayout.setBackgroundFragment(mapFragment);
        mapLayout.setListener(this);

        //set new toolbar layout as action bar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.main_title);
        toolbar.setPadding(0, statusBarHeight, 0, 0);
        appBarLayout = (AppBarLayout) findViewById(R.id.app_bar);

        //remove compatibility shadow on API >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View compatShadow = appBarLayout.findViewById(R.id.compat_shadow);
            appBarLayout.removeView(compatShadow);
        }

        setSupportActionBar(toolbar);

        adView = (AdView) findViewById(R.id.adView);

        //show progress indicator for initial load
        if (savedInstanceState == null) {
            firstLaunch = true;
        } else { //reload from last saved state

            final PhotoItem lastItem = savedInstanceState.getParcelable(ACTIVE_ITEM_KEY);
            if (lastItem != null) {
                adView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        addInfoFragment(lastItem);
                        adView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            }
        }

        //check for Google Play Services
        GoogleApiAvailability gaa = GoogleApiAvailability.getInstance();
        int status = gaa.isGooglePlayServicesAvailable(this);

        if (status != ConnectionResult.SUCCESS) {
            getFragmentManager().beginTransaction().hide(mapFragment).commit();
            Dialog errDialog = gaa.getErrorDialog(this, status, 0);
            errDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
            errDialog.show();
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbar.inflateMenu(R.menu.menu);

        searchMenuItem = menu.findItem(R.id.action_search);

        final RelativeLayout searchLayout = (RelativeLayout) MenuItemCompat.getActionView(searchMenuItem);
        searchView = (DelayAutoCompleteTextView) searchLayout.findViewById(R.id.search_view);

        searchAdapter = new PlacesSuggestionAdapter(this, R.layout.support_simple_spinner_dropdown_item, googleApiClient, null, null);
        searchView.setAdapter(searchAdapter);
        searchView.setOnItemClickListener(new SuggestionClickListener());

        ImageView clearSearchButton = (ImageView) MenuItemCompat.getActionView(searchMenuItem).findViewById(R.id.clear_search);
        clearSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setText("");
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                setActionBarAlpha(true);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                setActionBarAlpha(false);
                return true;
            }
        });

        LayoutTransition transition = new LayoutTransition();
        ObjectAnimator appearingAnim = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
        ObjectAnimator disappearingAnim = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
        transition.setAnimator(LayoutTransition.APPEARING, appearingAnim);
//        transition.setAnimator(LayoutTransition.CHANGE_APPEARING, appearingAnim);
        transition.setAnimator(LayoutTransition.DISAPPEARING, disappearingAnim);
//        transition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, disappearingAnim);
        toolbar.setLayoutTransition(transition);
//        searchLayout.setLayoutTransition(transition);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (adView != null)
            adView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (adView != null)
            adView.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (googleApiClient != null && googleApiClient.isConnected())
            googleApiClient.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (adView != null)
            adView.destroy();

        handler.removeMessages(RESET_EXIT_STATE);
        exitToast.cancel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ACTIVE_ITEM_KEY, activeItem);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onMapReady(GoogleMap googleMap) {
        if (firstLaunch) {
            getLayoutInflater().inflate(R.layout.progress_layout, mapLayout);
            progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
            progressBar = (ProgressBar) progressLayout.findViewById(R.id.progressBar);
            new LoadPhotosTask(mapFragment.getClusterManager(), mapFragment.getCachedItems(), this).execute();
        }

        gMap = googleMap;
        getWindow().setBackgroundDrawable(null); //remove background overdraw

        mapPaddingTop = statusBarHeight;

        TypedValue actionBarHeight = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.actionBarSize, actionBarHeight, true)) {
            toolbarHeight = TypedValue.complexToDimensionPixelSize(actionBarHeight.data, getResources().getDisplayMetrics());
            mapPaddingTop += toolbarHeight;
        }

        if (adView != null) {
            adView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    adView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mapPaddingBottom = adView.getHeight();
                    gMap.setPadding(mapPaddingLeft, mapPaddingTop, mapPaddingRight, mapPaddingBottom);
                }
            });
        } else {
            gMap.setPadding(mapPaddingLeft, mapPaddingTop, mapPaddingRight, mapPaddingBottom);
        }
    }

    private void addInfoFragment(PhotoItem photoItem) {

        int adViewPadding;

        if (adView != null)
            adViewPadding = adView.getHeight();
        else
            adViewPadding = 0;

        photoInfoFragment = PhotoInfoFragment.newInstance(photoItem, adViewPadding);
        mapLayout.setPanelFragment(photoInfoFragment);
        activeItem = photoItem;
    }

    private void removeInfoFragment() {
        mapLayout.removePanelFragment();
        activeItem = null;
    }

    private void setActionBarAlpha(boolean turnOpaque) {
        final ColorDrawable toolbarDrawable = (ColorDrawable) toolbar.getBackground();

        //make action bar fully opaque if photo list fragment is showing, otherwise translucent
        int newColor = (turnOpaque) ? 0xFFFFFFFF : 0x80FFFFFF;
        int oldColor = toolbarDrawable.getColor();

        //fade between colors
        ValueAnimator anim = ValueAnimator.ofInt(oldColor, newColor);
        anim.setDuration(300);
        anim.setEvaluator(new ArgbEvaluator());
        anim.setInterpolator(null);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int tweenColor = (int) animation.getAnimatedValue();
                toolbarDrawable.setColor(tweenColor);
            }
        });
        anim.start();
    }

    public void showToolbar() {
        if (!toolbarHidden)
            return;

        int newHeight = statusBarHeight + toolbarHeight;
        ValueAnimator mapPaddingAnim = ValueAnimator.ofInt(statusBarHeight, newHeight);
        mapPaddingAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int topPadding = (int) animation.getAnimatedValue();
                gMap.setPadding(mapPaddingLeft, topPadding, mapPaddingRight, mapPaddingBottom);
            }
        });
        mapPaddingTop = newHeight;
        mapPaddingAnim.start();

        appBarLayout.animate()
                .setInterpolator(new DecelerateInterpolator())
                .translationY(0)
                .start();
        toolbarHidden = false;
    }

    public void hideToolbar() {
        if (toolbarHidden || gridFragmentShowing)
            return;

        int newHeight = statusBarHeight;
        ValueAnimator mapPaddingAnim = ValueAnimator.ofInt(statusBarHeight + toolbarHeight, newHeight);
        mapPaddingAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int topPadding = (int) animation.getAnimatedValue();
                gMap.setPadding(mapPaddingLeft, topPadding, mapPaddingRight, mapPaddingBottom);
            }
        });
        mapPaddingTop = newHeight;
        mapPaddingAnim.start();

        appBarLayout.animate()
                .setInterpolator(new AccelerateInterpolator())
                .translationY(-appBarLayout.getHeight())
                .start();
        toolbarHidden = true;
    }

    public void onMapClick(LatLng latLng) {

        Log.d(LOG_TAG, "onMapClick called");
        if (toolbarHidden)
            showToolbar();
        else
            hideToolbar();

        if (mapFragment.setLastMarkerInactive())
            removeInfoFragment();
    }

    @Override
    public void onPhotosReady(ArrayList<PhotoItem> markers) {

        if (progressLayout != null) {
            ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(progressLayout, "alpha", progressLayout.getAlpha(), 0f);
            fadeAnim.setDuration(500);
            fadeAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mapLayout.removeView(progressLayout);
                }
            });
            fadeAnim.start();
        }

        mapFragment.cacheItems(markers);
    }

    @Override
    public void onPhotoItemClick(PhotoItem item) {
        showToolbar();
        addInfoFragment(item);
    }

    @Override
    public void onClusterClick(Collection<PhotoItem> items) {
        if (gridFragmentShowing) //avoid duplicate fragments caused by extra tapping
            return;

        gridFragmentShowing = true;

        ArrayList<PhotoItem> markerArrayList = new ArrayList<PhotoItem>(items);
        PhotoGridFragment photoGridFragment = PhotoGridFragment.newInstance(markerArrayList, appBarLayout.getHeight());

        showToolbar();
        setActionBarAlpha(gridFragmentShowing);
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.map_layout, photoGridFragment, "photoGridFragment")
                .addToBackStack(null)
                .commit();
    }

    public void onCameraChange(CameraPosition cameraPosition) {
        LatLngBounds bounds = gMap.getProjection().getVisibleRegion().latLngBounds;

        if (searchAdapter != null)
            searchAdapter.setBounds(bounds);

        if (cameraPosition.zoom < lastZoom) {
            if (mapFragment.setLastMarkerInactive())
                removeInfoFragment();
            hideToolbar();
        }

        lastZoom = cameraPosition.zoom;
    }

    @Override
    public void onPhotoGridItemClick(PhotoItem photoItem) {
        mapFragment.setUnclusteredMarker(new MarkerOptions()
                .position(photoItem.getPosition())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        getFragmentManager().popBackStackImmediate(); //hide grid fragment

        gridFragmentShowing = false;
        setActionBarAlpha(gridFragmentShowing);
        addInfoFragment(photoItem);
    }

    @Override
    public void onPhotoGridFragmentShown() {
        if (mapFragment != null) {
            getFragmentManager().beginTransaction().hide(mapFragment).commit();
        }

        searchMenuItem.setVisible(false);
        toolbar.setTitle(R.string.select_photo);
    }

    @Override
    public void onPhotoGridFragmentHidden() {
        if (mapFragment != null) {
            getFragmentManager().beginTransaction().show(mapFragment).commit();
        }

        searchMenuItem.setVisible(true);
        toolbar.setTitle(R.string.main_title);
    }

    @Override
    public void onFinalHeightMeasured(int fragmentHeight) {
        Marker marker = mapFragment.getActiveMarker();

        if (marker == null)
            return;

        Projection projection = gMap.getProjection();

        Log.d(LOG_TAG, "fragment height: " + fragmentHeight);
        Log.d(LOG_TAG, "activity bottom: " + mapLayout.getBottom());

        int fragmentDeltaYFromCenter = (mapLayout.getBottom() - adView.getHeight())/2 - fragmentHeight;

        Point markerPoint = projection.toScreenLocation(marker.getPosition());

        Log.d(LOG_TAG, "marker screen location: " + markerPoint.x + ", " + markerPoint.y);

        //pan camera to position marker above fragment if needed
        if (fragmentDeltaYFromCenter < 0) {
            LatLng latLng = projection.fromScreenLocation(new Point(markerPoint.x, markerPoint.y - fragmentDeltaYFromCenter));
            gMap.animateCamera(CameraUpdateFactory.newLatLng(latLng), 500, null);
        } else {
            gMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 500, null);
        }
    }

    @Override
    public void onBackPressed() {
        mapFragment.setLastMarkerInactive();

        gridFragmentShowing = false;
        hideToolbar();
        setActionBarAlpha(gridFragmentShowing);

        if (MenuItemCompat.isActionViewExpanded(searchMenuItem)) {
            MenuItemCompat.collapseActionView(searchMenuItem);

        // Force checking the native fragment manager for a backstack rather than the support lib fragment manager.
        } else if (getFragmentManager().popBackStackImmediate()) {
            activeItem = null;
        } else {
            if (readyToExit) {
                super.onBackPressed();
            } else {
                exitToast.show();
                readyToExit = true;
                handler.sendMessageDelayed(handler.obtainMessage(RESET_EXIT_STATE), 3000);
            }
        }
    }

    private void moveCameraToLocation(LatLng latLng, LatLngBounds viewport) {

        if (viewport == null) {
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11));
        } else {
            gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(viewport, 16));
        }
    }

    //Google Places API
    @Override
    public void onConnected(Bundle connectionHint) {

        //request location permission for Android 6.0+
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, COARSE_LOCATION);
        } else {
           setUpLocation(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
        }
    }

    private void setUpLocation(Location lastLocation) {
        if (firstLaunch) {
            //zoom to current location
            if (lastLocation != null) {
                double latitude = lastLocation.getLatitude();
                double longitude = lastLocation.getLongitude();
                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 11));
            }
            firstLaunch = false;
        }

        if (adView != null) {
            AdRequest.Builder arBuilder = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .addTestDevice("BC1C504B6ABB299ED8D45E0663F4EA5C")
                    .addTestDevice("3B0B162556C7396C96335F2EBCCE5189");

            if (lastLocation != null)
                arBuilder.setLocation(lastLocation);

            AdRequest adRequest = arBuilder.build();
            adView.loadAd(adRequest);
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(LOG_TAG, "location api connection suspended");
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "location api connection failed");
    }

    @Override
    public void onPanelClosed(View panelView) {
        mapFragment.setLastMarkerInactive();
        photoInfoFragment.shouldAnimateExit(false);
        mapLayout.removePanelFragment();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == COARSE_LOCATION) {

            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setUpLocation(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
            } else {
                setUpLocation(null);
            }
        }
    }

    private class SuggestionClickListener implements AdapterView.OnItemClickListener, ResultCallback<PlaceBuffer>, View.OnClickListener {

        private PlacesSuggestionAdapter.PlaceSuggestion suggestion;

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            suggestion = searchAdapter.getItem(position);
            Places.GeoDataApi.getPlaceById(googleApiClient, suggestion.placeId).setResultCallback(this);
        }

        @Override
        public void onResult(PlaceBuffer places) {
            if (places.getStatus().isSuccess()) {
                Place result = places.get(0);
                moveCameraToLocation(result.getLatLng(), result.getViewport());
            } else {
                String str;

                switch (places.getStatus().getStatusCode()) {
                    case CommonStatusCodes.NETWORK_ERROR:
                    case CommonStatusCodes.TIMEOUT:
                        str = "Couldn't connect to the server";
                        break;

                    default:
                        str = "Couldn't load this location";
                }

                places.getStatus().getStatusCode();
                Snackbar snackbar = Snackbar.make(mapLayout, str, Snackbar.LENGTH_LONG);
                snackbar.setAction("Retry", this);
                snackbar.show();
            }

            places.release();

            searchView.setText("");
            MenuItemCompat.collapseActionView(searchMenuItem);
            Util.hideKeyboard(MainActivity.this);
        }

        //retry button
        @Override
        public void onClick(View v) {
            Places.GeoDataApi.getPlaceById(googleApiClient, suggestion.placeId).setResultCallback(this);
        }
    }

}