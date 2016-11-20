package com.litesplash.picmapper;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

import java.io.File;

public class PhotoItem implements ClusterItem, Parcelable {
    private double latitude;
    private double longitude;
    private Uri fileUri;
    private String filename;

    public PhotoItem(double latitude, double longitude, Uri fileUri, String filename) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.fileUri = fileUri;
        this.filename = filename;
    }

    public PhotoItem(double latitude, double longitude, File file) {
        this(latitude, longitude, Uri.fromFile(file), file.getName());
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

    public Uri getFileUri() {
        return fileUri;
    }

    public String getFilename() {
        return filename;
    }

    public void setPosition(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setPosition(LatLng newPosition) {
        setPosition(newPosition.latitude, newPosition.longitude);
    }

    protected PhotoItem(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        fileUri = in.readParcelable(Uri.class.getClassLoader());
        filename = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeParcelable(fileUri, flags);
        dest.writeString(filename);
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

        return (latitude == other.latitude  &&  longitude == other.longitude  &&  fileUri.equals(other.fileUri));
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + fileUri.hashCode();
        return result;
    }
}