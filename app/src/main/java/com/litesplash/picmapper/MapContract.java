package com.litesplash.picmapper;

import android.content.ContentResolver;

import java.util.Collection;

/**
 * Created by Eugene on 11/5/2017.
 */

public interface MapContract {

    interface View {
        void finishLoading();
        void showAlertDialog(int titleResId, int msgResId);
        void addClusterItems(Collection<PhotoItem> items);
    }

    interface Presenter {
        void loadPhotosFromStorage(ContentResolver contentResolver);
    }
}
