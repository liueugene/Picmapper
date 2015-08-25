package com.litesplash.photomap;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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

public class BaseMapFragment extends MapFragment implements OnMapReadyCallback,
        ClusterManager.OnClusterItemClickListener<PhotoItem>, ClusterManager.OnClusterClickListener<PhotoItem>,
        GoogleMap.OnCameraChangeListener, GoogleMap.OnMapClickListener, SlideUpPanelLayout.Listener {

    private static final String SAVE_OBJECTS = "saveObjects";
    private static final String LAST_ACTIVE_CLUSTER_ITEM = "lastActiveClusterItem";
    private static final String CACHED_ITEMS = "cachedItems";

    private GoogleMap gMap;
    private ClusterManager<PhotoItem> clusterManager;
    private CustomClusterRenderer<PhotoItem> renderer;

    private PhotoItem lastActiveClusterItem;
    private Marker lastActiveUnclusteredMarker;
    private ArrayList<PhotoItem> cachedItems;

    private Listener listener;

    private boolean firstLaunch = true;

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
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (firstLaunch)
            getMapAsync(this);
        else
            listener.onMapReady(gMap);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
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
        firstLaunch = false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        clusterManager = new ClusterManager<PhotoItem>(getActivity().getApplicationContext(), gMap);
        renderer = new CustomClusterRenderer<PhotoItem>(getActivity().getApplicationContext(), gMap, clusterManager);

        gMap.setMyLocationEnabled(true);
        gMap.setOnCameraChangeListener(this);
        gMap.setOnMarkerClickListener(clusterManager);
        gMap.setOnMapClickListener(this);
        gMap.setInfoWindowAdapter(new DummyInfoWindowAdapter());

        TypedValue actionBarSize = new TypedValue();
        if (getActivity().getTheme().resolveAttribute(R.attr.actionBarSize, actionBarSize, true))
            gMap.setPadding(0, TypedValue.complexToDimensionPixelSize(actionBarSize.data, getResources().getDisplayMetrics()), 0, 0);

        clusterManager.setRenderer(renderer);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterClickListener(this);

        new LoadPhotosTask(clusterManager, cachedItems, listener).execute();

        listener.onMapReady(googleMap);
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
        clusterManager.onCameraChange(cameraPosition);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        listener.onMapClick(latLng);
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

    public void cacheItems(ArrayList<PhotoItem> cachedItems) {
        if (cachedItems == null)
            this.cachedItems = cachedItems;
    }

    @Override
    public void onPanelClosed(View panelView) {
        setLastMarkerInactive();
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
            Log.d(MainActivity.TAG, "dummy view shown for info window");
            return view;
        }

        @Override
        public View getInfoContents(Marker marker) {
            return null;
        }
    }
}
