package com.litesplash.photomap;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

import java.io.File;

public class PhotoMarker implements ClusterItem, Parcelable {
    private final double latitude;
    private final double longitude;
    private String filePath;

    public PhotoMarker(double lat, double lng, String f) {
        latitude = lat;
        longitude = lng;
        filePath = f;
    }

    public PhotoMarker(double lat, double lng, File f) {
        latitude = lat;
        longitude = lng;
        filePath = f.getAbsolutePath();
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

    public String getFilePath() {
        return filePath;
    }

    public File getFile() {
        return new File(filePath);
    }

    protected PhotoMarker(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        filePath = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(filePath);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<PhotoMarker> CREATOR = new Parcelable.Creator<PhotoMarker>() {
        @Override
        public PhotoMarker createFromParcel(Parcel in) {
            return new PhotoMarker(in);
        }

        @Override
        public PhotoMarker[] newArray(int size) {
            return new PhotoMarker[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PhotoMarker))
            return false;

        PhotoMarker other = (PhotoMarker) o;

        return (latitude == other.latitude  &&  longitude == other.longitude  &&  filePath.equals(other.filePath));
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + filePath.hashCode();
        return result;
    }
}