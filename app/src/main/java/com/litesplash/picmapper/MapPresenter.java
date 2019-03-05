package com.litesplash.picmapper;

import android.content.ContentResolver;

import java.util.ArrayList;

/**
 * Created by Eugene on 11/5/2017.
 */

public class MapPresenter implements MapContract.Presenter, LoadPhotosTask.Callback {

    private MapContract.View mapView;

    private ArrayList<PhotoItem> taggedItems;
    private ArrayList<PhotoItem> untaggedItems;
    private PhotoItem activePhotoItem;

    public MapPresenter(MapContract.View mapView) {
        this.mapView = mapView;
    }

    public void loadPhotosFromStorage(ContentResolver contentResolver) {
        new LoadPhotosTask(contentResolver, taggedItems, this).execute();
    }

    @Override
    public void onPhotosReady(ArrayList<PhotoItem> taggedItems, ArrayList<PhotoItem> untaggedItems) {
        //alert the user if no geotagged photos are found
        if (taggedItems.isEmpty()) {
            mapView.showAlertDialog(R.string.no_photos_dialog_title, R.string.no_photos_dialog_msg);
        }

        mapView.addClusterItems(taggedItems);

        this.taggedItems = taggedItems;
        this.untaggedItems = untaggedItems;

        mapView.finishLoading();
    }

    public ArrayList<PhotoItem> getTaggedItems() {
        return taggedItems;
    }

    public void setTaggedItems(ArrayList<PhotoItem> taggedItems) {
        this.taggedItems = taggedItems;
    }

}
