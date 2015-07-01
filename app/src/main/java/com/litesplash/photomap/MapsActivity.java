package com.litesplash.photomap;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class MapsActivity extends Activity implements ClusterManager.OnClusterItemClickListener<PhotoMarker>, ClusterManager.OnClusterClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener {

    protected static final String TAG = "PhotoMap";

    private GoogleMap gMap; // Might be null if Google Play services APK is not available.
    private ClusterManager<PhotoMarker> clusterManager;
    private DefaultClusterRenderer<PhotoMarker> renderer;

    private PhotoMarker lastActiveMarker;
    private float lastZoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #gMap} is not null.
     * <p/>
     * If it isn't installed {@link MapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (gMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            gMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (gMap != null) {
                setUpMap();
            }
        }
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
        clusterManager.getMarkerCollection().setOnInfoWindowAdapter(new PhotoInfoWindowAdapter());

        new LoadPhotosTask().execute();
    }


    private void removeInfoFrag(FragmentManager fm) {
        PhotoInfoFragment infoFrag = (PhotoInfoFragment) fm.findFragmentByTag("photoInfoFragment");

        if (infoFrag != null) {
            fm.beginTransaction()
                    .setCustomAnimations(R.animator.slide_up, R.animator.slide_down, R.animator.slide_up, R.animator.slide_down)
                    .remove(infoFrag)
                    .commit();
        }
    }

    public boolean onClusterItemClick(PhotoMarker photoMarker) {
        getActionBar().show();

        Bundle bundle = new Bundle();
        bundle.putSerializable("photoMarker", photoMarker);

        PhotoInfoFragment infoFragment = new PhotoInfoFragment();
        infoFragment.setArguments(bundle);

        FragmentManager fm = getFragmentManager();

        removeInfoFrag(fm);

        fm.beginTransaction()
                .setCustomAnimations(R.animator.slide_up, R.animator.slide_down, R.animator.slide_up, R.animator.slide_down)
                .add(R.id.maps_activity, infoFragment, "photoInfoFragment")
                .addToBackStack(null)
                .commit();

        final Marker m = renderer.getMarker(photoMarker);
        m.showInfoWindow();

        //pan camera to marker position
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(m.getPosition());
        gMap.animateCamera(cameraUpdate, 500, null);

        lastActiveMarker = photoMarker;
        return true;
    }

    public boolean onClusterClick(Cluster cluster) {
        Collection<PhotoMarker> markerCollection = cluster.getItems();
        renderer.getMarker(cluster).showInfoWindow();
        return false;
    }

    public void onMapClick(LatLng latLng) {
        ActionBar bar = getActionBar();
        if(bar.isShowing())
            bar.hide();
        else
            bar.show();

        removeInfoFrag(getFragmentManager());
    }

    public void onCameraChange(CameraPosition cameraPosition) {

        if (cameraPosition.zoom < lastZoom) {
            Marker m = renderer.getMarker(lastActiveMarker);

            if (m != null) {
                m.hideInfoWindow();
                removeInfoFrag(getFragmentManager());
            }

            ActionBar actionBar = getActionBar();
            if (actionBar != null)
                actionBar.hide();
        }

        lastZoom = cameraPosition.zoom;
        clusterManager.onCameraChange(cameraPosition);
    }

    @Override
    public void onBackPressed() {

        Marker m = renderer.getMarker(lastActiveMarker);

        if(m != null) {
            m.hideInfoWindow();
        }

        ActionBar actionBar = getActionBar();
        if(actionBar != null)
            actionBar.hide();

        super.onBackPressed();
    }

    private class LoadPhotosTask extends AsyncTask<Void, Void, Void> {

        protected Void doInBackground(Void... params) {
            Queue<File> dirs = new LinkedList<File>();

            dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
            ExifInterface exifInterface;

            while (!dirs.isEmpty()) {
                File[] files = dirs.remove().listFiles();

                for (int i = 0; i < files.length; i++) {

                    if (files[i].isDirectory()) {
                        dirs.add(files[i]);
                        continue;
                    }

                    try {
                        //load latitude/longitude coordinates from EXIF data
                        exifInterface = new ExifInterface(files[i].getAbsolutePath());
                        float[] latLon = new float[2];

                        if (exifInterface.getLatLong(latLon)) {
                            PhotoMarker m = new PhotoMarker(latLon[0], latLon[1], files[i]);
                            clusterManager.addItem(m);
                        }

                    } catch (IOException e) {
                        Log.e(TAG, "IO exception when loading photo");
                    }

                }
            }
            return null;
        }

        protected void onPostExecute(Void aVoid) {
            clusterManager.cluster();
        }
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
            //new LoadBitmapTask(marker).execute(pm);

            return actualView;
        }

        private class LoadBitmapTask extends AsyncTask<PhotoMarker, Void, Bitmap> {

            private Marker marker;

            public LoadBitmapTask(Marker m) {
                super();
                marker = m;
            }

            protected Bitmap doInBackground(PhotoMarker... params) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4; //TODO calculate for screen size

                return BitmapFactory.decodeFile(params[0].getFile().getPath(), options);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {

                //check that marker is still active before trying to display
                if(marker.isInfoWindowShown()) {
                    actualView.setImageBitmap(bitmap);
                    marker.showInfoWindow();
                }
            }
        }
    }

}