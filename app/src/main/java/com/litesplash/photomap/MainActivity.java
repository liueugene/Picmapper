package com.litesplash.photomap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
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

    protected static final String TAG = "PhotoMap";

    private static final String MAP_FRAGMENT_TAG = "mapFragment";
    private static final String ACTIVE_ITEM_KEY = "activeItem";
    private static final int RESET_EXIT_STATE = 0x2E5E7;

    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    private MenuItem searchMenuItem;
    private AutoCompleteTextView searchView;
    private SlideUpPanelLayout mapLayout;

    private RelativeLayout progressLayout;
    private ProgressBar progressBar;

    private BaseMapFragment mapFragment;
    private PhotoInfoFragment photoInfoFragment;

    private GoogleMap gMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient googleApiClient;
    /*
    private ClusterManager<PhotoItem> clusterManager;
    private CustomClusterRenderer<PhotoItem> renderer;

    private ArrayList<PhotoItem> allMarkers;
    */

    private PlacesSuggestionAdapter searchAdapter;

    /*
    private Marker lastActiveUnclusteredMarker;
    */

    private PhotoItem activeItem;

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
                Log.d(TAG, "readyToExit set to false");
                a.readyToExit = false;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        FragmentManager fm = getFragmentManager();

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

        //show progress indicator for initial load
        if (savedInstanceState == null) {
            getLayoutInflater().inflate(R.layout.progress_layout, mapLayout);
            progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
            progressBar = (ProgressBar) progressLayout.findViewById(R.id.progressBar);
            firstLaunch = true;
        } else { //reload from last saved state
            PhotoItem lastItem = savedInstanceState.getParcelable(ACTIVE_ITEM_KEY);
            if (lastItem != null) {
                addInfoFragment(lastItem);
            }
        }

        //set new toolbar layout as action bar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        appBarLayout = (AppBarLayout) findViewById(R.id.app_bar);

        //remove compatibility shadow on API >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View compatShadow = appBarLayout.findViewById(R.id.compat_shadow);
            appBarLayout.removeView(compatShadow);
        }

        setSupportActionBar(toolbar);
/*
        if (savedInstanceState != null) {
            lastActiveClusterItem = savedInstanceState.getParcelable("lastActiveClusterItem");
            allMarkers = savedInstanceState.getParcelableArrayList("allMarkers");
        }
        */

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
    protected void onStop() {
        super.onStop();

        if (googleApiClient != null && googleApiClient.isConnected())
            googleApiClient.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeMessages(RESET_EXIT_STATE);
        exitToast.cancel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
/*
        outState.putParcelable("lastActiveClusterItem", lastActiveClusterItem);
        outState.putParcelableArrayList("allMarkers", allMarkers);
*/
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
        gMap = googleMap;
        /*
        clusterManager = new ClusterManager<PhotoItem>(this, gMap);
        renderer = new CustomClusterRenderer<PhotoItem>(this, gMap, clusterManager, lastActiveClusterItem, new CustomClusterRenderer.OnLastActiveMarkerRenderedListener<PhotoItem>() {
            @Override
            public void onLastActiveMarkerRendered(PhotoItem clusterItem, Marker marker) {
                setActiveMarker(marker);
            }
        });

        gMap.setMyLocationEnabled(true);
        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(clusterManager);
        gMap.setOnMapClickListener(this);
        gMap.setInfoWindowAdapter(clusterManager.getMarkerManager());

        TypedValue actionBarSize = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.actionBarSize, actionBarSize, true))
            gMap.setPadding(0, TypedValue.complexToDimensionPixelSize(actionBarSize.data, getResources().getDisplayMetrics()), 0, 0);

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);

        new LoadPhotosTask(clusterManager, allMarkers, new LoadPhotosTask.Callback() {
            @Override
            public void onPhotosReady(ArrayList<PhotoItem> markers) {
                ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(progressLayout, "alpha", progressLayout.getAlpha(), 0f);
                fadeAnim.setDuration(500);
                fadeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mapLayout.removeView(progressLayout);
                    }
                });
                fadeAnim.start();

                if (markers == null)
                    allMarkers = markers;
            }
        }).execute();
        */
        getWindow().setBackgroundDrawable(null); //remove background overdraw
    }

    private void zoomToCurrentLocation() {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if (lastLocation == null) {
            Log.d(TAG, "last location null");
            return;
        }
        double latitude = lastLocation.getLatitude();
        double longitude = lastLocation.getLongitude();

        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 11));
    }

    private void addInfoFragment(PhotoItem photoItem) {
        /*
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        PhotoInfoFragment photoInfo = (PhotoInfoFragment) fm.findFragmentById(R.id.photo_info_fragment);

        Bundle bundle = new Bundle();
        bundle.putParcelable(PhotoInfoFragment.PHOTO_ITEM_TAG, photoItem);

        photoInfo.updateInfo(bundle);

        fm.beginTransaction().show(photoInfo).addToBackStack(null).commit();
        */

        photoInfoFragment = PhotoInfoFragment.newInstance(photoItem);
        mapLayout.setPanelFragment(photoInfoFragment);
        activeItem = photoItem;
    }

    private void removeInfoFragment() {
        getFragmentManager().popBackStackImmediate();
        activeItem = null;
    }

    private void setActionBarAlpha(boolean turnOpaque) {
        final ColorDrawable toolbarDrawable = (ColorDrawable) toolbar.getBackground();

        //make action bar fully opaque if photo list fragment is showing, otherwise translucent
        int newColor = (turnOpaque) ? 0xFFFFFFFF : 0x80FFFFFF;
        int oldColor = toolbarDrawable.getColor();

        //fade between colors
        ValueAnimator anim = ValueAnimator.ofInt(oldColor, newColor);
        anim.setDuration(500);
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

        ValueAnimator mapPaddingAnim = ValueAnimator.ofInt(0, appBarLayout.getBottom());
        mapPaddingAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int topPadding = (int) animation.getAnimatedValue();
                gMap.setPadding(0, topPadding, 0, 0);
            }
        });
        mapPaddingAnim.start();

        appBarLayout.animate()
                .setInterpolator(new DecelerateInterpolator())
                .translationY(0)
                .start();
        toolbarHidden = false;
    }

    public void hideToolbar() {

        if (toolbarHidden)
            return;

        ValueAnimator mapPaddingAnim = ValueAnimator.ofInt(appBarLayout.getBottom(), 0);
        mapPaddingAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int topPadding = (int) animation.getAnimatedValue();
                gMap.setPadding(0, topPadding, 0, 0);
            }
        });
        mapPaddingAnim.start();

        appBarLayout.animate()
                .setInterpolator(new AccelerateInterpolator())
                .translationY(-appBarLayout.getBottom())
                .start();
        toolbarHidden = true;
    }
/*
    public boolean onClusterItemClick(PhotoItem photoItem) {
        showToolbar();

        Marker marker = renderer.getMarker(photoItem);

        if (!photoItem.equals(lastActiveClusterItem)) {
            setLastMarkerInactive();
        }

        lastActiveClusterItem = photoItem;
        addInfoFragment(photoItem);
        setActiveMarker(marker);

        return true;
    }

    private boolean setLastMarkerInactive() {
        if (lastActiveUnclusteredMarker != null) {
            lastActiveUnclusteredMarker.remove();
            lastActiveUnclusteredMarker = null;
            return true;
        }

        if (lastActiveClusterItem != null) {
            Marker marker = renderer.getMarker(lastActiveClusterItem);
            if (marker != null) {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker());
                lastActiveClusterItem = null;
                return true;
            }
        }

        return false;
    }

    private void setActiveMarker(Marker marker) {
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        marker.showInfoWindow();
    }


    public boolean onClusterClick(Cluster cluster) {
        setLastMarkerInactive();
        ArrayList<PhotoItem> markerArrayList = new ArrayList<PhotoItem>(cluster.getItems());
        PhotoGridFragment photoGridFragment = PhotoGridFragment.newInstance(markerArrayList);

        gridFragmentShowing = true;
        showToolbar();
        setActionBarAlpha(gridFragmentShowing);
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.maps_activity_layout, photoGridFragment, "photoGridFragment")
                .addToBackStack(null)
                .commit();

        renderer.getMarker(cluster).showInfoWindow();
        return true;
    }
*/

    public void onMapClick(LatLng latLng) {

        if (toolbarHidden)
            showToolbar();
        else
            hideToolbar();

        if (mapFragment.setLastMarkerInactive())
            removeInfoFragment();
    }

    @Override
    public void onPhotosReady(ArrayList<PhotoItem> markers) {
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(progressLayout, "alpha", progressLayout.getAlpha(), 0f);
        fadeAnim.setDuration(500);
        fadeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mapLayout.removeView(progressLayout);
            }
        });
        fadeAnim.start();

        mapFragment.cacheItems(markers);
    }

    @Override
    public void onPhotoItemClick(PhotoItem item) {
        showToolbar();
        addInfoFragment(item);
    }

    @Override
    public void onClusterClick(Collection<PhotoItem> items) {
        ArrayList<PhotoItem> markerArrayList = new ArrayList<PhotoItem>(items);
        PhotoGridFragment photoGridFragment = PhotoGridFragment.newInstance(markerArrayList);

        gridFragmentShowing = true;
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
        /*
        Marker marker = gMap.addMarker(new MarkerOptions()
                .position(photoItem.getPosition())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        lastActiveUnclusteredMarker = marker;
        */
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
        FragmentManager fm = getFragmentManager();

        if (mapFragment != null) {
            fm.beginTransaction().hide(mapFragment).commit();
        }
    }

    @Override
    public void onPhotoGridFragmentHidden() {
        FragmentManager fm = getFragmentManager();

        if (mapFragment != null) {
            fm.beginTransaction().show(mapFragment).commit();
        }
    }

    @Override
    public void onFinalHeightMeasured(int fragmentHeight) {
        Marker marker = mapFragment.getActiveMarker();
/*
        if (lastActiveUnclusteredMarker == null) {
            marker = renderer.getMarker(lastActiveClusterItem);
        } else {
            marker = lastActiveUnclusteredMarker;
        }
*/
        if (marker == null)
            return;

        Projection projection = gMap.getProjection();

        Log.d(TAG, "fragment height: " + fragmentHeight);
        Log.d(TAG, "activity bottom: " + mapLayout.getBottom());

        int fragmentDeltaYFromCenter = mapLayout.getBottom()/2 - fragmentHeight;

        Point markerPoint = projection.toScreenLocation(marker.getPosition());

        Log.d(TAG, "marker screen location: " + markerPoint.x + ", " + markerPoint.y);

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
        /*
        Marker marker = renderer.getMarker(lastActiveClusterItem);

        if (marker != null) {
            marker.hideInfoWindow();
            marker.setIcon(BitmapDescriptorFactory.defaultMarker());
        }
        */

        gridFragmentShowing = false;
        hideToolbar();
        setActionBarAlpha(gridFragmentShowing);

        if (MenuItemCompat.isActionViewExpanded(searchMenuItem)) {
            MenuItemCompat.collapseActionView(searchMenuItem);

        // Force checking the native fragment manager for a backstack rather than the support lib fragment manager.
        } else if (!getFragmentManager().popBackStackImmediate()) {
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
        if (firstLaunch) {
            zoomToCurrentLocation();
            firstLaunch = false;
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "location api connection suspended");
        googleApiClient.connect();
        //TODO fill in
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "location api connection failed");
        //TODO fill in
    }

    @Override
    public void onPanelClosed(View panelView) {
        mapFragment.setLastMarkerInactive();
        photoInfoFragment.shouldAnimateExit(false);
        mapLayout.removePanelFragment();
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