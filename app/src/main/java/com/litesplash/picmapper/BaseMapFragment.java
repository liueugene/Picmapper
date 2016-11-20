package com.litesplash.picmapper;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

import java.util.ArrayList;
import java.util.Collection;

public class BaseMapFragment extends MapFragment implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
        ClusterManager.OnClusterItemClickListener<PhotoItem>, ClusterManager.OnClusterClickListener<PhotoItem>,
        GoogleMap.OnCameraChangeListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerDragListener {

    private static final String LOG_TAG = "BaseMapFragment";

    private static final String SAVE_OBJECTS = "saveObjects";
    private static final String LAST_ACTIVE_CLUSTER_ITEM = "lastActiveClusterItem";
    private static final String CACHED_ITEMS = "cachedItems";

    private GoogleMap gMap;
    private ClusterManager<PhotoItem> clusterManager;
    private CustomClusterRenderer<PhotoItem> renderer;

    private PhotoItem lastActiveClusterItem;
    private Marker lastActiveUnclusteredMarker;
    private ArrayList<PhotoItem> cachedItems;

    private PhotoItem testItem;

    private Listener listener;

    private boolean mapReady;
    private boolean activityReady;

    public static BaseMapFragment newInstance() {
        return new BaseMapFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (savedInstanceState != null) {
            Bundle saveObjects = savedInstanceState.getBundle(SAVE_OBJECTS);

            lastActiveClusterItem = saveObjects.getParcelable(LAST_ACTIVE_CLUSTER_ITEM);
            cachedItems = saveObjects.getParcelableArrayList(CACHED_ITEMS);
        }

        getMapAsync(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activityReady = true;

        if (mapReady) {
            listener.onMapReady(gMap);
        }

        Log.d(LOG_TAG, "clusterManager: " + clusterManager);
        Log.d(LOG_TAG, "renderer: " + renderer);
        Log.d(LOG_TAG, "cachedItems: " + cachedItems);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement BaseMapFragment.Listener");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        //must save custom objects within another bundle to avoid ClassNotFoundException when
        //unparceled by another ClassLoader in the API
        Bundle saveObjects = new Bundle();
        saveObjects.putParcelable(LAST_ACTIVE_CLUSTER_ITEM, lastActiveClusterItem);
        saveObjects.putParcelableArrayList(CACHED_ITEMS, cachedItems);
        outState.putBundle(SAVE_OBJECTS, saveObjects);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(this);
        gMap.setOnMapClickListener(this);
        gMap.setOnMarkerDragListener(this);
        gMap.setInfoWindowAdapter(new DummyInfoWindowAdapter());

        clusterManager = new ClusterManager<PhotoItem>(getActivity().getApplicationContext(), gMap);
        renderer = new CustomClusterRenderer<PhotoItem>(getActivity().getApplicationContext(), gMap, clusterManager);

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);

        if (cachedItems != null) {
            clusterManager.addItems(cachedItems);
        }

        if (activityReady) {
            listener.onMapReady(googleMap);
        }

        if (BuildConfig.DEBUG) {
            createTestMarker();
        }

        mapReady = true;
    }

    public void enableMyLocation() throws SecurityException {
        gMap.setMyLocationEnabled(true);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {

        if (BuildConfig.DEBUG && "test".equals(marker.getTitle())) {
            listener.onPhotoItemClick(testItem);
            return true;
        }

        return clusterManager.onMarkerClick(marker);
    }

    @Override
    public boolean onClusterItemClick(PhotoItem photoItem) {
        Marker marker = renderer.getMarker(photoItem);

        if (!photoItem.equals(lastActiveClusterItem)) {
            setLastMarkerInactive();
        }

        lastActiveClusterItem = photoItem;
        listener.onPhotoItemClick(photoItem);
        setActiveMarker(marker);

        return true;
    }

    @Override
    public boolean onClusterClick(Cluster<PhotoItem> cluster) {
        setLastMarkerInactive();
        listener.onClusterClick(cluster.getItems());
        return true;
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        listener.onCameraChange(cameraPosition);
        clusterManager.onCameraIdle();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        listener.onMapClick(latLng);
    }

    public void setMapType(int type) {
        gMap.setMapType(type);
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

    public ClusterManager<PhotoItem> getClusterManager() {
        return clusterManager;
    }

    public void cacheItems(ArrayList<PhotoItem> cachedItems) {
        this.cachedItems = cachedItems;
    }

    public ArrayList<PhotoItem> getCachedItems() {
        return cachedItems;
    }

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

    public void createTestMarker() {
        testItem = new PhotoItem(0, 0, (Uri)null, "test");
        gMap.addMarker(new MarkerOptions().position(testItem.getPosition()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)).draggable(true).title("test"));
    }

    public interface Listener extends LoadPhotosTask.Callback {
        void onMapReady(GoogleMap googleMap);
        void onPhotoItemClick(PhotoItem item);
        void onClusterClick(Collection<PhotoItem> items);
        void onCameraChange(CameraPosition cameraPosition);
        void onMapClick(LatLng latLng);
    }

    //used to force the API to bring the selected marker to front
    private class DummyInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private View view;

        public DummyInfoWindowAdapter() {
            view = new View(getActivity().getApplicationContext());
        }
        @Override
        public View getInfoWindow(Marker marker) {
            Log.d(LOG_TAG, "dummy view shown for info window");
            return view;
        }

        @Override
        public View getInfoContents(Marker marker) {
            return null;
        }
    }
}
