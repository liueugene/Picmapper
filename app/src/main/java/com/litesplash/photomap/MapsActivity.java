package com.litesplash.photomap;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, ClusterManager.OnClusterItemClickListener<PhotoMarker>,
        ClusterManager.OnClusterClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener, PhotoListFragment.PhotoListItemClickListener {

    protected static final String TAG = "PhotoMap";

    private GoogleMap gMap; // Might be null if Google Play services APK is not available.
    private ClusterManager<PhotoMarker> clusterManager;
    private DefaultClusterRenderer<PhotoMarker> renderer;

    private PhotoMarker lastActiveMarker;
    private Marker lastActiveNonClusteredMarker;
    private float lastZoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(null); //remove background overdraw
        setContentView(R.layout.activity_maps);

        ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(gMap == null)
            ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
    }

    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        setUpMap();
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #gMap} is not null.
     */
    private void setUpMap() {
        clusterManager = new ClusterManager<PhotoMarker>(this, gMap);
        renderer = new DefaultClusterRenderer<PhotoMarker>(this, gMap, clusterManager);

        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(clusterManager);
        gMap.setOnMapClickListener(this);
        gMap.setInfoWindowAdapter(clusterManager.getMarkerManager());

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);
//        clusterManager.getMarkerCollection().setOnInfoWindowAdapter(new PhotoInfoWindowAdapter());

        new LoadPhotosTask(clusterManager).execute();
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

    public boolean onClusterItemClick(PhotoMarker photoMarker) {
        getSupportActionBar().show();

        addInfoFragment(photoMarker);

        final Marker m = renderer.getMarker(photoMarker);
        m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        m.showInfoWindow();

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

    public boolean onClusterClick(Cluster cluster) {
        ArrayList<PhotoMarker> markerArrayList = new ArrayList(cluster.getItems());
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("markerArrayList", markerArrayList);

        PhotoListFragment photoListFragment = new PhotoListFragment();
        photoListFragment.setArguments(bundle);

        ActionBar actionBar = getSupportActionBar();
        actionBar.show();
        actionBar.setBackgroundDrawable(new ColorDrawable(0xffffffff));
        removeInfoFragment();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.setCustomAnimations(R.animator.slide_up, 0, 0, R.animator.slide_down)
                .add(R.id.maps_activity, photoListFragment, "photoListFragment")
                .addToBackStack(null)
                .commit();

        //renderer.getMarker(cluster).showInfoWindow();
        return true;
    }

    public void onMapClick(LatLng latLng) {
        ActionBar bar = getSupportActionBar();
        if(bar.isShowing())
            bar.hide();
        else
            bar.show();

        Marker lastMarker = renderer.getMarker(lastActiveMarker);
        if (lastMarker != null) {
            lastMarker.setIcon(BitmapDescriptorFactory.defaultMarker());
            lastActiveMarker = null;
        }

        removeInfoFragment();
    }

    public void onCameraChange(CameraPosition cameraPosition) {

        if (cameraPosition.zoom < lastZoom) {
            Marker m = renderer.getMarker(lastActiveMarker);

            if (m != null) {
                m.hideInfoWindow();
                m.setIcon(BitmapDescriptorFactory.defaultMarker());
                removeInfoFragment();
            } else if (lastActiveNonClusteredMarker != null) {
                removeInfoFragment();
            }

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null)
                actionBar.hide();
        }

        lastZoom = cameraPosition.zoom;
        clusterManager.onCameraChange(cameraPosition);
    }

    @Override
    public void onPhotoListItemClick(PhotoMarker photoMarker) {
        FragmentManager fm = getFragmentManager();
        Fragment listFragment = fm.findFragmentByTag("photoListFragment");
        Fragment mapFragment = fm.findFragmentById(R.id.map);

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

        addInfoFragment(photoMarker);

        //pan camera to marker position
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(photoMarker.getPosition());
        gMap.animateCamera(cameraUpdate, 500, null);

        lastActiveNonClusteredMarker = marker;
    }

    @Override
    public void onBackPressed() {

        if (lastActiveNonClusteredMarker != null) {
            lastActiveNonClusteredMarker.remove();
            lastActiveNonClusteredMarker = null;
        }

        Marker m = renderer.getMarker(lastActiveMarker);

        if(m != null) {
            m.hideInfoWindow();
            m.setIcon(BitmapDescriptorFactory.defaultMarker());
        }

        ActionBar actionBar = getSupportActionBar();

        if(actionBar != null)
            actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.translucent_white)));
            actionBar.hide();

        // Force checking the native fragment manager for a backstack rather than
        // the support lib fragment manager.
        if (!getFragmentManager().popBackStackImmediate()) {
            super.onBackPressed();
        }

        Fragment mapFragment = getFragmentManager().findFragmentById(R.id.map);

        if (mapFragment == null)
            Log.d(TAG, "map fragment is null");
        else
            Log.d(TAG, "map fragment hidden: " + mapFragment.isHidden());
    }

    public class PhotoInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private ImageView actualView;
        private boolean imageLoaded;
        private Marker lastMarker;

        public View getInfoWindow(Marker marker) {
            return null;
        }

        public View getInfoContents(Marker marker) {

            //show final result when done loading
            if(imageLoaded && lastMarker.equals(marker)) {
                return actualView;
            }

            imageLoaded = false;
            lastMarker = marker;
            actualView = new ImageView(getApplicationContext());
            PhotoMarker pm = renderer.getClusterItem(marker);

            int width = getResources().getDisplayMetrics().widthPixels;

            final Marker finalizedMarker = marker;

            Picasso.with(getApplicationContext())
                    .load(pm.getFile())
                    .resize(width/2, width/2)
                    .onlyScaleDown()
                    .centerInside()
                    .noFade()
                    .into(actualView, new Callback() {
                        @Override
                        public void onSuccess() {
                            imageLoaded = true;
                            finalizedMarker.showInfoWindow();
                        }

                        @Override
                        public void onError() {

                        }
                    });

            return actualView;
        }
    }

}