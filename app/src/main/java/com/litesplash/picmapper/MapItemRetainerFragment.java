package com.litesplash.picmapper;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.app.Fragment;

import java.util.ArrayList;

/**
 * Retains a collection of PhotoItems for the map to load on a configuration change.
 * A fragment is needed instead of saving the items to savedInstanceState, or else a
 * TransactionTooLarge exception could occur for huge lists of photos (more apparent on 7.0+).
 */
public class MapItemRetainerFragment extends Fragment {

    private ArrayList<PhotoItem> items;

    public MapItemRetainerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setPhotoItems(ArrayList<PhotoItem> items) {
        this.items = items;
    }

    public ArrayList<PhotoItem> getPhotoItems() {
        return items;
    }

}
