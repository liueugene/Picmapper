package com.litesplash.picmapper;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import java.util.ArrayList;

/**
 * Created by Eugene on 7/13/2015.
 */
public class LoadPhotosTask extends AsyncTask<Void, Void, LoadPhotosTask.MarkerLists> {

    private static final String LOG_TAG = "LoadPhotosTask";
    private ContentResolver contentResolver;
    private ArrayList<PhotoItem> taggedItems;
    private ArrayList<PhotoItem> untaggedItems;
    private Callback callback;

    public LoadPhotosTask(ContentResolver contentResolver, ArrayList<PhotoItem> taggedItems, Callback callback) {
        this.contentResolver = contentResolver;
        this.taggedItems = taggedItems;
        this.callback = callback;
    }

    protected MarkerLists doInBackground(Void... params) {

        untaggedItems = new ArrayList<PhotoItem>();

        if (taggedItems == null) {
            taggedItems = new ArrayList<PhotoItem>();

            String[] projection = {MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.LATITUDE,
                    MediaStore.Images.Media.LONGITUDE};

            Cursor cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

            if (cursor == null)
                return new MarkerLists(taggedItems, untaggedItems);

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

                if (latitude == 0f && longitude == 0f)
                    untaggedItems.add(new PhotoItem(latitude, longitude, uri, name));
                else
                    taggedItems.add(new PhotoItem(latitude, longitude, uri, name));
            }

            cursor.close();
        }

        return new MarkerLists(taggedItems, untaggedItems);
    }

    protected void onPostExecute(MarkerLists m) {
        if (callback != null) {
            callback.onPhotosReady(m.taggedItems, m.untaggedItems);
        }
    }

    static class MarkerLists {
        ArrayList<PhotoItem> taggedItems;
        ArrayList<PhotoItem> untaggedItems;

        MarkerLists(ArrayList<PhotoItem> taggedItems, ArrayList<PhotoItem> untaggedItems) {
            this.taggedItems = taggedItems;
            this.untaggedItems = untaggedItems;
        }
    }

    public interface Callback {
        void onPhotosReady(ArrayList<PhotoItem> taggedItems, ArrayList<PhotoItem> untaggedItems);
    }
}
