package com.litesplash.picmapper;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
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
public class LoadPhotosTask extends AsyncTask<Void, Void, ArrayList<PhotoItem>> {

    private static final String LOG_TAG = "LoadPhotosTask";
    private WeakReference<ClusterManager<PhotoItem>> clusterManagerRef;
    private ContentResolver contentResolver;
    private ArrayList<PhotoItem> markers;
    private Callback callback;

    public LoadPhotosTask(ClusterManager<PhotoItem> clusterManager, ContentResolver contentResolver, ArrayList<PhotoItem> existingMarkers, Callback callback) {
        clusterManagerRef = new WeakReference<ClusterManager<PhotoItem>>(clusterManager);
        this.contentResolver = contentResolver;
        this.markers = existingMarkers;
        this.callback = callback;
    }

    protected ArrayList<PhotoItem> doInBackground(Void... params) {

        if (markers == null) {
            markers = new ArrayList<PhotoItem>();

            String[] projection = {MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.LATITUDE,
                    MediaStore.Images.Media.LONGITUDE};

            Cursor cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

            if (cursor == null)
                return markers;

            int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
            int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            int latIndex = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE);
            int lonIndex = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE);

            while (cursor.moveToNext()) {

                long id = cursor.getLong(idIndex);
                String name = cursor.getString(nameIndex);
                double latitude = cursor.getDouble(latIndex);
                double longitude = cursor.getDouble(lonIndex);
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.toString(id));

                markers.add(new PhotoItem(latitude, longitude, uri, name));
            }

            cursor.close();
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
