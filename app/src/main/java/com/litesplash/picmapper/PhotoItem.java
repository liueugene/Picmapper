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
    private Uri contentUri;
    private String filename;
    private long id;

    public PhotoItem(double latitude, double longitude, Uri contentUri, String filename, long id) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.contentUri = contentUri;
        this.filename = filename;
        this.id = id;
    }

    public PhotoItem(double latitude, double longitude, File file) {
        this(latitude, longitude, Uri.fromFile(file), file.getName(), 0);
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

    public Uri getContentUri() {
        return contentUri;
    }

    public String getFilename() {
        return filename;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return filename;
    }

    public String getSnippet() {
        return "";
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
        contentUri = in.readParcelable(Uri.class.getClassLoader());
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
        dest.writeParcelable(contentUri, flags);
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

        return (latitude == other.latitude  &&  longitude == other.longitude  &&  contentUri.equals(other.contentUri));
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + contentUri.hashCode();
        return result;
    }
}