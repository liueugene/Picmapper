package com.litesplash.photomap;

import com.google.android.gms.maps.model.MarkerOptions;
import java.io.File;

public class Photo {

    private File file;
    private float[] latLon;

    public Photo(File f, float[] ll) {
        file = f;
        latLon = ll;
    }

    public File getFile() {
        return file;
    }

    public float getLatitude() {
        return latLon[0];
    }

    public float getLongitude() {
        return latLon[1];
    }
}