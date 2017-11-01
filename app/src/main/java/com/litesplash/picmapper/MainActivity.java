package com.litesplash.picmapper;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
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
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnCameraChangeListener, GoogleMap.OnMarkerClickListener, ClusterManager.OnClusterItemClickListener<PhotoItem>, ClusterManager.OnClusterClickListener<PhotoItem>, PhotoGridFragment.Listener,
        PhotoInfoFragment.PhotoInfoFragmentListener, LoadPhotosTask.Callback, SlideUpPanelLayout.Listener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = "MainActivity";
    private static final String RETAINER_FRAGMENT_TAG = "MapItemRetainerFragment";

    private static final String MAP_FRAGMENT_TAG = "mapFragment";
    private static final String ACTIVE_ITEM_KEY = "activeItem";
    private static final int RESET_EXIT_STATE = 0x2E5E7;
    private static final int FINE_LOCATION_REQUEST = 0;
    private static final int OPEN_DIR_REQUEST = 1;
    private static final int READ_STORAGE_REQUEST = 2;
    private static final int MULTI_REQUEST = 3;

    private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    private DrawerLayout drawerLayout;
    private NavigationView navDrawer;
    private ActionBarDrawerToggle drawerToggle;

    private RelativeLayout searchLayout;
    private MenuItem searchMenuItem;
    private AutoCompleteTextView searchView;
    private SlideUpPanelLayout mapLayout;

    private RelativeLayout progressLayout;
    private ProgressBar progressBar;

    private AdView adView;

    private MapFragment mapFragment;
    private PhotoInfoFragment photoInfoFragment;
    private MapItemRetainerFragment retainerFragment;

    private GoogleMap gMap;
    private int mapPaddingLeft;
    private int mapPaddingTop;
    private int mapPaddingRight;
    private int mapPaddingBottom;

    private GoogleApiClient googleApiClient;

    private PlacesSuggestionAdapter searchAdapter;

    private PhotoItem activeItem;
    private ArrayList<PhotoItem> taggedItems;
    private ArrayList<PhotoItem> untaggedItems;

    private int toolbarHeight;
    private int statusBarHeight;
    private float lastZoom;
    private boolean gridFragmentShowing;
    private boolean toolbarHidden;

    private boolean firstLaunch;
    private boolean readyToExit = false;
    private Toast exitToast;

    // pasted from original BaseMapFragment class
    private static final String SAVE_OBJECTS = "saveObjects";
    private static final String LAST_ACTIVE_CLUSTER_ITEM = "lastActiveClusterItem";
    private static final String LOADED_ITEMS = "loadedItems";

    private ClusterManager<PhotoItem> clusterManager;
    private CustomClusterRenderer<PhotoItem> renderer;

    private PhotoItem lastActiveClusterItem;
    private Marker lastActiveUnclusteredMarker;

    private PhotoItem testItem;

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

        // retainer fragment for if the user has a large list of photos that can't fit into the savedInstanceState bundle
        // find existing fragment on activity restart
        retainerFragment = (MapItemRetainerFragment) fm.findFragmentByTag(RETAINER_FRAGMENT_TAG);

        // otherwise create new retainer
        if (retainerFragment == null) {
            retainerFragment = new MapItemRetainerFragment();
            fm.beginTransaction().add(retainerFragment, RETAINER_FRAGMENT_TAG).commit();
        } else {
            taggedItems = retainerFragment.getPhotoItems();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId != 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resId);
            }
        }

        exitToast = Toast.makeText(this, "Press back again to exit.", Toast.LENGTH_SHORT);

        //set new toolbar layout as action bar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.main_title);
        toolbar.setPadding(0, statusBarHeight, 0, 0);
        appBarLayout = (AppBarLayout) findViewById(R.id.app_bar);

        /*
        //remove compatibility shadow on API >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View compatShadow = appBarLayout.findViewById(R.id.compat_shadow);
            appBarLayout.removeView(compatShadow);
        }
        */

        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navDrawer = (NavigationView) findViewById(R.id.nav_drawer);
        navDrawer.inflateHeaderView(R.layout.nav_drawer_header);
        navDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {

                if (item.getItemId() == R.id.untagged_photos) {
                    if (untaggedItems == null)
                        return false;

                    drawerLayout.closeDrawers();
                    showPhotoGrid(untaggedItems, PhotoGridFragment.UNTAGGED);
                    return true;
                }


                int type;
                switch (item.getItemId()) {
                    case R.id.satellite_view:
                        type = GoogleMap.MAP_TYPE_HYBRID;
                        break;
                    case R.id.terrain_view:
                        type = GoogleMap.MAP_TYPE_TERRAIN;
                        break;
                    default:
                        type = GoogleMap.MAP_TYPE_NORMAL;
                }

                if (gMap != null)
                    gMap.setMapType(type);
                drawerLayout.closeDrawers();
                return true;
            }
        });

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mapLayout = (SlideUpPanelLayout) findViewById(R.id.map_layout);
        mapLayout.setFragmentManager(fm);

        //initialize map
        mapFragment = (MapFragment) fm.findFragmentByTag(MAP_FRAGMENT_TAG);

        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(mapFragment, MAP_FRAGMENT_TAG).commit();
        }

        mapFragment.getMapAsync(this);

        mapLayout.setBackgroundFragment(mapFragment);
        mapLayout.setListener(this);

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

            lastActiveClusterItem = savedInstanceState.getParcelable(LAST_ACTIVE_CLUSTER_ITEM);
        }

        //check for Google Play Services
        GoogleApiAvailability gaa = GoogleApiAvailability.getInstance();
        int status = gaa.isGooglePlayServicesAvailable(this);

        if (status != ConnectionResult.SUCCESS) {
            getFragmentManager().beginTransaction().hide(mapFragment).commit();
            Dialog errDialog = gaa.getErrorDialog(this, status, 0);
            errDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
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

        searchLayout = (RelativeLayout) MenuItemCompat.getActionView(searchMenuItem);
        searchView = (DelayAutoCompleteTextView) searchLayout.findViewById(R.id.search_view);

        //originally R.layout.support_simple_spinner_dropdown_item
        searchAdapter = new PlacesSuggestionAdapter(this, R.layout.search_suggestion_item, googleApiClient, null, null);
        searchView.setAdapter(searchAdapter);
        searchView.setOnItemClickListener(new SuggestionClickListener());
        //searchView.setDropDownVerticalOffset(16);

        ImageButton clearSearchButton = (ImageButton) searchLayout.findViewById(R.id.clear_search);
        clearSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setText("");
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                //setActionBarOpaque(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);

                //show keyboard
                searchView.requestFocus();
                //must delay slightly for some reason (race condition?)
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(searchView, 0);
                    }
                }, 200);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                //setActionBarOpaque(false);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);

                InputMethodManager imm = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);

                return true;
            }
        });
/*
        LayoutTransition transition = new LayoutTransition();
        ObjectAnimator appearingAnim = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
        ObjectAnimator disappearingAnim = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
        transition.setAnimator(LayoutTransition.APPEARING, appearingAnim);
        transition.setAnimator(LayoutTransition.DISAPPEARING, disappearingAnim);
        toolbar.setLayoutTransition(transition);
        searchLayout.setLayoutTransition(transition);
*/
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(drawerToggle.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
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

        // app is quitting, no need for retainer fragment
        if (isFinishing()) {
            FragmentManager fm = getFragmentManager();
            fm.beginTransaction().remove(retainerFragment).commit();
        }
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
        outState.putParcelable(LAST_ACTIVE_CLUSTER_ITEM, lastActiveClusterItem);
        retainerFragment.setPhotoItems(taggedItems);

    }

    public void onMapReady(GoogleMap googleMap) {
        if (firstLaunch) {
            ArrayList<String> toRequest = new ArrayList<>();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                toRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);

            if (!toRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, toRequest.toArray(new String[toRequest.size()]), MULTI_REQUEST);
                return;
            } else {
                getLayoutInflater().inflate(R.layout.progress_layout, mapLayout);
                progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
                progressBar = (ProgressBar) progressLayout.findViewById(R.id.progressBar);
            }
        }

        new LoadPhotosTask(getContentResolver(), taggedItems, this).execute();

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


        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(this);
        gMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                testItem.setPosition(marker.getPosition());
            }
        });

        clusterManager = new ClusterManager<PhotoItem>(getApplicationContext(), gMap);
        renderer = new CustomClusterRenderer<PhotoItem>(getApplicationContext(), gMap, clusterManager);

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);

        if (BuildConfig.DEBUG) {
            createTestMarker();
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

    private void setActionBarOpaque(boolean turnOpaque) {
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

        if (setLastMarkerInactive())
            removeInfoFragment();
    }

    @Override
    public void onPhotosReady(ArrayList<PhotoItem> taggedItems, ArrayList<PhotoItem> untaggedItems) {

        clusterManager.addItems(taggedItems);
        clusterManager.cluster();

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

        //alert the user if no geotagged photos are found
        if (taggedItems.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.no_photos_dialog_msg);
            builder.setTitle(R.string.no_photos_dialog_title);
            builder.setPositiveButton("OK", null);

            AlertDialog dialog = builder.create();
            dialog.show();
        }

        this.taggedItems = taggedItems;
        this.untaggedItems = untaggedItems;

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (BuildConfig.DEBUG && "test".equals(marker.getTitle())) {
            onPhotoItemClick(testItem);
            return true;
        }

        return clusterManager.onMarkerClick(marker);
    }

    public void onPhotoItemClick(PhotoItem item) {
        showToolbar();
        addInfoFragment(item);
    }

    @Override
    public boolean onClusterItemClick(PhotoItem photoItem) {
        Marker marker = renderer.getMarker(photoItem);

        if (!photoItem.equals(lastActiveClusterItem)) {
            setLastMarkerInactive();
        }

        lastActiveClusterItem = photoItem;
        onPhotoItemClick(photoItem);
        setActiveMarker(marker);

        return true;
    }

    @Override
    public boolean onClusterClick(Cluster<PhotoItem> cluster) {
        setLastMarkerInactive();
        showPhotoGrid(cluster.getItems(), PhotoGridFragment.GEOTAGGED);
        return true;
    }

    public boolean setLastMarkerInactive() {
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

    public void setUnclusteredMarker(MarkerOptions options) {
        lastActiveUnclusteredMarker = gMap.addMarker(options);
        lastActiveUnclusteredMarker.showInfoWindow();
    }

    public void setActiveMarker(Marker marker) {
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        marker.showInfoWindow();
    }

    public Marker getActiveMarker() {
        if (lastActiveUnclusteredMarker == null) {
            return renderer.getMarker(lastActiveClusterItem);
        } else {
            return lastActiveUnclusteredMarker;
        }
    }

    private void createTestMarker() {
        testItem = new PhotoItem(0, 0, (Uri)null, "test");
        gMap.addMarker(new MarkerOptions().position(testItem.getPosition()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)).draggable(true).title("test"));
    }

    private void showPhotoGrid(Collection<PhotoItem> items, int photoType) {
        if (gridFragmentShowing) //avoid duplicate fragments caused by extra tapping
            return;

        gridFragmentShowing = true;

        ArrayList<PhotoItem> markerArrayList = new ArrayList<PhotoItem>(items);
        PhotoGridFragment photoGridFragment = PhotoGridFragment.newInstance(markerArrayList, appBarLayout.getHeight(), photoType);

        showToolbar();
        //setActionBarOpaque(gridFragmentShowing);
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.map_layout, photoGridFragment, "photoGridFragment")
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        LatLngBounds bounds = gMap.getProjection().getVisibleRegion().latLngBounds;

        if (searchAdapter != null)
            searchAdapter.setBounds(bounds);

        if (cameraPosition.zoom < lastZoom) {
            if (setLastMarkerInactive())
                removeInfoFragment();
            hideToolbar();
        }

        lastZoom = cameraPosition.zoom;

        clusterManager.onCameraIdle();
    }

    @Override
    public void onPhotoGridItemClick(PhotoItem photoItem, int photoType) {

        if (photoType == PhotoGridFragment.GEOTAGGED) {
            setUnclusteredMarker(new MarkerOptions()
                    .position(photoItem.getPosition())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            getFragmentManager().popBackStackImmediate(); //hide grid fragment
            gridFragmentShowing = false;
            //setActionBarOpaque(false);
            addInfoFragment(photoItem);

        } else { //not geotagged, launch photo tagging activity
            Intent intent = new Intent(this, TagLocationActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onPhotoGridFragmentShown() {
        /*
        if (mapFragment != null) {
            getFragmentManager().beginTransaction().hide(mapFragment).commit();
        }
        */

        searchMenuItem.setVisible(false);
        toolbar.setTitle(R.string.select_photo);
    }

    @Override
    public void onPhotoGridFragmentHidden() {
        /*
        if (mapFragment != null) {
            getFragmentManager().beginTransaction().show(mapFragment).commit();
        }
        */

        searchMenuItem.setVisible(true);
        toolbar.setTitle(R.string.main_title);
    }

    @Override
    public void onFinalHeightMeasured(int fragmentHeight) {
        Marker marker = getActiveMarker();

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
        setLastMarkerInactive();

        gridFragmentShowing = false;
        hideToolbar();
        //setActionBarOpaque(gridFragmentShowing);

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setUpLocation(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
            gMap.setMyLocationEnabled(true);
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
        setLastMarkerInactive();
        photoInfoFragment.shouldAnimateExit(false);
        mapLayout.removePanelFragment();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MULTI_REQUEST) {

            ArrayList<String> denied = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    setUpGrantedPermission(permissions[i]);
                } else {
                    denied.add(permissions[i]);
                }
            }

            if (denied.size() > 1) {
                final String[] deniedPermissions = denied.toArray(new String[denied.size()]);
                Snackbar snackbar = Snackbar.make(mapLayout, "Picmapper needs additional permissions", Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction("Allow", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ActivityCompat.requestPermissions(MainActivity.this, deniedPermissions, MULTI_REQUEST);
                    }
                });
                snackbar.show();
            } else if (denied.size() > 0) {
                final String[] deniedPermission = denied.toArray(new String[denied.size()]);
                String snackbarMsg;
                int duration = Snackbar.LENGTH_LONG;
                if (denied.get(0).equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    snackbarMsg = "Picmapper would like to access your location";
                } else if (denied.get(0).equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    snackbarMsg = "Picmapper needs permission to load photos";
                    duration = Snackbar.LENGTH_INDEFINITE;
                } else { //something went wrong, should not reach here
                    snackbarMsg = "Picmapper needs your permission";
                }

                Snackbar snackbar = Snackbar.make(mapLayout, snackbarMsg, duration);
                snackbar.setAction("Allow", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ActivityCompat.requestPermissions(MainActivity.this, deniedPermission, MULTI_REQUEST);
                    }
                });
                snackbar.show();
            }
        }
    }

    @SuppressWarnings({"MissingPermission"})
    private void setUpGrantedPermission(String permission) {
        if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            setUpLocation(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
            gMap.setMyLocationEnabled(true);

        } else if (permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            getLayoutInflater().inflate(R.layout.progress_layout, mapLayout);
            progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
            progressBar = (ProgressBar) progressLayout.findViewById(R.id.progressBar);
            new LoadPhotosTask(getContentResolver(), taggedItems, this).execute();
        }
    }

    private void handleUntaggedPhotos() {

    }

    private void openAdditionalPhotos() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(Intent.createChooser(intent, "Get photos from"), OPEN_DIR_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OPEN_DIR_REQUEST) {

            ArrayList<Uri> photoUris = new ArrayList<Uri>();
            photoUris.add(data.getData());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                ClipData clipData = data.getClipData();

                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        photoUris.add(clipData.getItemAt(i).getUri());
                    }
                }
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