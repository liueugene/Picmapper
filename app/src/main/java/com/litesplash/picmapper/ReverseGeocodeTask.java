package com.litesplash.picmapper;

import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * Created by Eugene on 7/28/2015.
 */
class ReverseGeocodeTask extends AsyncTask<Double, Void, String> {

    private static final String TAG = "ReverseGeocodeTask";
    private Geocoder geocoder;
    private Callback callback;

    public ReverseGeocodeTask(Geocoder geocoder, Callback callback) {
        this.geocoder = geocoder;
        this.callback = callback;
    }

    protected String doInBackground(Double... params) {

        String locationStr = null;

        double latitude = params[0];
        double longitude = params[1];

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses == null || addresses.isEmpty())
                return "Unknown location";

            StringBuilder sb = new StringBuilder(32);
            Address addr = addresses.get(0);


            int maxIndex = addr.getMaxAddressLineIndex();
            boolean firstItem = true;

            for (int i = 0; i <= maxIndex; i++) {
                if (firstItem)
                    firstItem = false;
                else
                    sb.append(", ");
                sb.append(addr.getAddressLine(i));

                Log.d(TAG, i + ": " + addr.getAddressLine(i));
            }

            locationStr = sb.toString();

            Log.d(TAG, locationStr);

        } catch (IOException e) {
            Log.e(TAG, "IO exception when reverse geocoding latLon " + latitude + ", " + longitude);
        }

        if (locationStr == null || locationStr.isEmpty())
            locationStr = "Unknown location";

        return locationStr;
    }

    @Override
    protected void onPostExecute(String location) {
        if (callback != null)
            callback.onLocationFound(location);
    }

    public interface Callback {
        void onLocationFound(String location);
    }
}
