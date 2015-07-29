package com.litesplash.photomap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, ClusterManager.OnClusterItemClickListener<PhotoMarker>,
        ClusterManager.OnClusterClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener, PhotoListFragment.PhotoListFragmentListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    protected static final String TAG = "PhotoMap";

    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    private GoogleMap gMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient googleApiClient;
    private ClusterManager<PhotoMarker> clusterManager;
    private CustomClusterRenderer<PhotoMarker> renderer;

    private ArrayList<PhotoMarker> allMarkers;

    private PlacesSuggestionAdapter searchAdapter;

    private PhotoMarker lastActiveMarker;
    private Marker lastActiveNonClusteredMarker;
    private float lastZoom;
    private boolean listFragmentShowing;
    private boolean toolbarHidden;

    private MenuItem searchMenuItem;
    private AutoCompleteTextView searchView;
    private RelativeLayout activityLayout;
    private RelativeLayout progressLayout;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        activityLayout = (RelativeLayout) findViewById(R.id.maps_activity);

        //show progress indicator
        getLayoutInflater().inflate(R.layout.progress_layout, activityLayout);
        progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
        progressBar = (ProgressBar) progressLayout.findViewById(R.id.progressBar);

        //set new toolbar layout as action bar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        appBarLayout = (AppBarLayout) findViewById(R.id.app_bar);

        //remove compatibility shadow on API >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View compatShadow = appBarLayout.findViewById(R.id.compat_shadow);
            appBarLayout.removeView(compatShadow);
        }

        setSupportActionBar(toolbar);

        if (savedInstanceState != null) {
            lastActiveMarker = savedInstanceState.getParcelable("lastActiveMarker");
            allMarkers = savedInstanceState.getParcelableArrayList("allMarkers");
        }

        ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
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

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (googleApiClient != null && googleApiClient.isConnected())
            googleApiClient.disconnect();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        outState.putParcelable("lastActiveMarker", lastActiveMarker);
        outState.putParcelableArrayList("allMarkers", allMarkers);

        super.onSaveInstanceState(outState);
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
        setUpMap();
        getWindow().setBackgroundDrawable(null); //remove background overdraw
    }

    private void setUpMap() {
        clusterManager = new ClusterManager<PhotoMarker>(this, gMap);
        renderer = new CustomClusterRenderer<PhotoMarker>(this, gMap, clusterManager, lastActiveMarker, new CustomClusterRenderer.OnLastActiveMarkerRenderedListener<PhotoMarker>() {
            @Override
            public void onLastActiveMarkerRendered(PhotoMarker clusterItem, Marker marker) {
                setActiveMarker(marker);
            }
        });

        gMap.setMyLocationEnabled(true);
        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(clusterManager);
        gMap.setOnMapClickListener(this);
        gMap.setInfoWindowAdapter(clusterManager.getMarkerManager());

        TypedValue actionBarSizeTv = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.actionBarSize, actionBarSizeTv, true))
            gMap.setPadding(0, TypedValue.complexToDimensionPixelSize(actionBarSizeTv.data, getResources().getDisplayMetrics()), 0, 0);

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);
//        clusterManager.getMarkerCollection().setOnInfoWindowAdapter(new PhotoInfoWindowAdapter());

        new LoadPhotosTask(clusterManager, allMarkers, new LoadPhotosTask.Callback() {
            @Override
            public void onPhotosReady(ArrayList<PhotoMarker> markers) {
                ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(progressLayout, "alpha", progressLayout.getAlpha(), 0f);
                fadeAnim.setDuration(500);
                fadeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        activityLayout.removeView(progressLayout);
                    }
                });
                fadeAnim.start();

                if (markers == null)
                    allMarkers = markers;
            }
        }).execute();
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

    private void addInfoFragment(PhotoMarker photoMarker) {
        removeInfoFragment();
        PhotoInfoFragment infoFragment = PhotoInfoFragment.newInstance(photoMarker);

        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.maps_activity, infoFragment, "photoInfoFragment")
                .addToBackStack(null)
                .commit();
    }

    private void removeInfoFragment() {
        FragmentManager fm = getFragmentManager();
        PhotoInfoFragment infoFrag = (PhotoInfoFragment) fm.findFragmentByTag("photoInfoFragment");

        if (infoFrag != null) {/*
            fm.beginTransaction()
                    .setCustomAnimations(0, R.animator.slide_down)
                    .remove(infoFrag)
                    .commit();
                    */
            fm.popBackStackImmediate();

            if (lastActiveNonClusteredMarker != null) {
                lastActiveNonClusteredMarker.remove();
                lastActiveNonClusteredMarker = null;
            }
        }
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

    public boolean onClusterItemClick(PhotoMarker photoMarker) {
        showToolbar();

        addInfoFragment(photoMarker);
        Marker m = setActiveMarker(photoMarker);

        if (!photoMarker.equals(lastActiveMarker)) {
            Marker lastMarker = renderer.getMarker(lastActiveMarker);
            if (lastMarker != null)
                lastMarker.setIcon(BitmapDescriptorFactory.defaultMarker());
        }

        //pan camera to marker position
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(m.getPosition());
        gMap.animateCamera(cameraUpdate, 500, null);

        lastActiveMarker = photoMarker;
        return true;
    }

    private Marker setActiveMarker(PhotoMarker photoMarker) {
        Marker m = renderer.getMarker(photoMarker);

        if (m != null) {
            setActiveMarker(m);
        }
        return m;
    }

    private void setActiveMarker(Marker marker) {
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        marker.showInfoWindow();
    }

    public boolean onClusterClick(Cluster cluster) {
        ArrayList<PhotoMarker> markerArrayList = new ArrayList<PhotoMarker>(cluster.getItems());
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("markerArrayList", markerArrayList);

        PhotoListFragment photoListFragment = new PhotoListFragment();
        photoListFragment.setArguments(bundle);

        listFragmentShowing = true;
        showToolbar();
        setActionBarAlpha(listFragmentShowing);
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.maps_activity, photoListFragment, "photoListFragment")
                .addToBackStack(null)
                .commit();

        renderer.getMarker(cluster).showInfoWindow();
        return true;
    }

    public void onMapClick(LatLng latLng) {

        if (toolbarHidden)
            showToolbar();
        else
            hideToolbar();

        Marker lastMarker = renderer.getMarker(lastActiveMarker);
        if (lastMarker != null) {
            lastMarker.setIcon(BitmapDescriptorFactory.defaultMarker());
            lastActiveMarker = null;
        }

        removeInfoFragment();
    }

    public void onCameraChange(CameraPosition cameraPosition) {

        LatLngBounds bounds = gMap.getProjection().getVisibleRegion().latLngBounds;
        searchAdapter.setBounds(bounds);

        if (cameraPosition.zoom < lastZoom) {
            Marker m = renderer.getMarker(lastActiveMarker);

            if (m != null) {
                m.hideInfoWindow();
                m.setIcon(BitmapDescriptorFactory.defaultMarker());
                removeInfoFragment();
            } else if (lastActiveNonClusteredMarker != null) {
                removeInfoFragment();
            }

            hideToolbar();
        }

        lastZoom = cameraPosition.zoom;
        clusterManager.onCameraChange(cameraPosition);
    }

    @Override
    public void onPhotoListItemClick(PhotoMarker photoMarker) {
        FragmentManager fm = getFragmentManager();
        Fragment listFragment = fm.findFragmentByTag("photoListFragment");

        if (listFragment != null) {
            /*
            fm.beginTransaction().setCustomAnimations(0, R.animator.slide_down)
                    .remove(listFragment)
                    .commit();
                    */
            fm.popBackStackImmediate();
        }

        Marker marker = gMap.addMarker(new MarkerOptions()
                .position(photoMarker.getPosition())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        listFragmentShowing = false;
        setActionBarAlpha(listFragmentShowing);
        addInfoFragment(photoMarker);

        //pan camera to marker position
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(photoMarker.getPosition());
        gMap.animateCamera(cameraUpdate, 500, null);

        lastActiveNonClusteredMarker = marker;
    }

    @Override
    public void onPhotoListFragmentShown() {
        FragmentManager fm = getFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map);

        if (mapFragment != null) {
            fm.beginTransaction().hide(mapFragment).commit();
        }
    }

    @Override
    public void onPhotoListFragmentHidden() {
        FragmentManager fm = getFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map);

        if (mapFragment != null) {
            fm.beginTransaction().show(mapFragment).commit();
        }
    }

    @Override
    public void onBackPressed() {

        if (lastActiveNonClusteredMarker != null) {
            lastActiveNonClusteredMarker.remove();
            lastActiveNonClusteredMarker = null;
        }

        Marker m = renderer.getMarker(lastActiveMarker);

        if (m != null) {
            m.hideInfoWindow();
            m.setIcon(BitmapDescriptorFactory.defaultMarker());
        }

        listFragmentShowing = false;
        hideToolbar();
        setActionBarAlpha(listFragmentShowing);

        if (MenuItemCompat.isActionViewExpanded(searchMenuItem)) {
            MenuItemCompat.collapseActionView(searchMenuItem);
            // Force checking the native fragment manager for a backstack rather than the support lib fragment manager.
        } else if (!getFragmentManager().popBackStackImmediate()) {
            super.onBackPressed();
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
        zoomToCurrentLocation();
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
                        str = "Couldn't connect to the location provider";
                        break;

                    default:
                        str = "Couldn't load this location";
                }

                places.getStatus().getStatusCode();
                Snackbar snackbar = Snackbar.make(activityLayout, str, Snackbar.LENGTH_LONG);
                snackbar.setAction("Retry", this);
                snackbar.show();
            }

            places.release();

            searchView.setText("");
            MenuItemCompat.collapseActionView(searchMenuItem);

            View view = getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }

        //retry button
        @Override
        public void onClick(View v) {
            Places.GeoDataApi.getPlaceById(googleApiClient, suggestion.placeId).setResultCallback(this);
        }
    }

}