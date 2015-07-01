package com.litesplash.photomap;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

import java.io.File;
import java.io.Serializable;

public class PhotoMarker implements ClusterItem, Serializable {
    private final double latitude;
    private final double longitude;
    private File file;

    public PhotoMarker(double lat, double lng, File f) {
        latitude = lat;
        longitude = lng;
        file = f;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public LatLng getPosition() {
        return new LatLng(latitude, longitude);
    }

    public File getFile() {
        return file;
    }
}