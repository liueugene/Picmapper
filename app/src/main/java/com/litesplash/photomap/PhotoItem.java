package com.litesplash.photomap;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

import java.io.File;

public class PhotoItem implements ClusterItem, Parcelable {
    private final double latitude;
    private final double longitude;
    private int filenameIndex;
    private String filePath;

    public PhotoItem(double latitude, double longitude, String filePath) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.filePath = filePath;

        filenameIndex = filePath.lastIndexOf('/') + 1;
    }

    public PhotoItem(double latitude, double longitude, File file) {
        this(latitude, longitude, file.getAbsolutePath());
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

    public String getFilename() {
        return filePath.substring(filenameIndex);
    }

    protected PhotoItem(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        filenameIndex = in.readInt();
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
        dest.writeInt(filenameIndex);
        dest.writeString(filePath);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<PhotoItem> CREATOR = new Parcelable.Creator<PhotoItem>() {
        @Override
        public PhotoItem createFromParcel(Parcel in) {
            return new PhotoItem(in);
        }

        @Override
        public PhotoItem[] newArray(int size) {
            return new PhotoItem[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PhotoItem))
            return false;

        PhotoItem other = (PhotoItem) o;

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