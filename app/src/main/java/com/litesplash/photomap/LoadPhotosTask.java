package com.litesplash.photomap;

import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.google.maps.android.clustering.ClusterManager;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Eugene on 7/13/2015.
 */
class LoadPhotosTask extends AsyncTask<Void, Void, ArrayList<PhotoItem>> {

    private WeakReference<ClusterManager<PhotoItem>> clusterManagerRef;
    private ArrayList<PhotoItem> markers;
    private Callback callback;

    public LoadPhotosTask(ClusterManager<PhotoItem> clusterManager, ArrayList<PhotoItem> existingMarkers, Callback callback) {
        clusterManagerRef = new WeakReference<ClusterManager<PhotoItem>>(clusterManager);
        this.markers = existingMarkers;
        this.callback = callback;
    }

    protected ArrayList<PhotoItem> doInBackground(Void... params) {

        if (markers == null) {
            markers = new ArrayList<PhotoItem>();
            Queue<File> dirs = new LinkedList<File>();

            dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
            Log.d(MainActivity.TAG, "photo directory: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
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
                            PhotoItem m = new PhotoItem(latLon[0], latLon[1], files[i]);
                            markers.add(m);
                        }

                    } catch (IOException e) {
                        Log.e(MainActivity.TAG, "IO exception when loading photo");
                    }

                }
            }
        }

        ClusterManager<PhotoItem> clusterManager = clusterManagerRef.get();

        if (clusterManager != null)
            clusterManager.addItems(markers);

        return markers;
    }

    protected void onPostExecute(ArrayList<PhotoItem> markers) {

        ClusterManager<PhotoItem> clusterManager = clusterManagerRef.get();

        if (clusterManager != null) {
            clusterManager.cluster();
        }

        if (callback != null) {
            callback.onPhotosReady(markers);
        }
    }

    public interface Callback {
        void onPhotosReady(ArrayList<PhotoItem> markers);
    }
}
