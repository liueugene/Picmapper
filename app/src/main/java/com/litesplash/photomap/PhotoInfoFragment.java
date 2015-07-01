package com.litesplash.photomap;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;

public class PhotoInfoFragment extends Fragment {

    private static Context context;
    private static int animDuration;

    public PhotoInfoFragment() {
        // Required empty public constructor
    }


    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View infoOverlay = inflater.inflate(R.layout.photo_info_overlay, container, false);
        context = infoOverlay.getContext();
        animDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        updateInfo(infoOverlay, getArguments());
        return infoOverlay;
    }

    //public info updater method for an existing fragment
    public void updateInfo(Bundle bundle) {
        updateInfo(getView(), bundle);
    }

    //private updater that can be called by onCreateView, passing in view to inflate as parameter
    private void updateInfo(View infoOverlayView, Bundle bundle) {

        TextView filename = (TextView) infoOverlayView.findViewById(R.id.filename);
        TextView latData = (TextView) infoOverlayView.findViewById(R.id.latitude_data_text);
        TextView lonData = (TextView) infoOverlayView.findViewById(R.id.longitude_data_text);
        TextView locText = (TextView) infoOverlayView.findViewById(R.id.location_text);

        PhotoMarker pm = (PhotoMarker) bundle.getSerializable("photoMarker");

        filename.setText(pm.getFile().getName());
        latData.setText(String.format("%.5f", pm.getLatitude()));
        lonData.setText(String.format("%.5f", pm.getLongitude()));

        new ReverseGeocodeTask(locText).execute(pm.getLatitude(), pm.getLongitude());
    }

    private class ReverseGeocodeTask extends AsyncTask<Double, Void, String> {

        private TextView locText;

        public ReverseGeocodeTask(TextView locText) {
            super();
            this.locText = locText;
        }

        protected String doInBackground(Double... params) {
            double latitude = params[0];
            double longitude = params[1];
            Geocoder geocoder = new Geocoder(context);
            String locationStr = null;

            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses == null || addresses.isEmpty())
                    return "Unknown location";

                Address addr = addresses.get(0);

                String city = addr.getLocality();
                String state = addr.getAdminArea();
                String zip = addr.getPostalCode();

                locationStr = (city == null) ? "" : city + ", ";
                locationStr += (state == null) ? "" : state + " ";
                locationStr += (zip == null) ? "" : zip;

            } catch (IOException e) {
                Log.e(MapsActivity.TAG, "IO exception when reverse geocoding latLon " + latitude + ", " + longitude);
            }

            if(locationStr.isEmpty())
                locationStr = "Unknown location";

            return locationStr;
        }

        @Override
        protected void onPostExecute(String locationStr) {
            locText.setAlpha(0);
            locText.setText(locationStr);
            locText.animate().alpha(1).setDuration(animDuration);
        }
    }
}
