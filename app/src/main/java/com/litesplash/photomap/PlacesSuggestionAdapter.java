package com.litesplash.photomap;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.AutocompletePredictionBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Eugene on 7/19/2015.
 */
public class PlacesSuggestionAdapter extends ArrayAdapter {

    private static final LatLngBounds globalBounds = new LatLngBounds(new LatLng(-90, -180), new LatLng(90, 180));

    private GoogleApiClient googleApiClient;
    private LatLngBounds latLngBounds;
    private AutocompleteFilter autoCompFilter;
    private ArrayList<PlaceSuggestion> suggestions;

    private LayoutInflater inflater;
    private int resource;
    private int fieldId = 0;

    public PlacesSuggestionAdapter(Context context, int resource, GoogleApiClient client, LatLngBounds bounds, AutocompleteFilter filter) {
        super(context, resource);
        googleApiClient = client;
        latLngBounds = (bounds == null) ? globalBounds : bounds;
        autoCompFilter = filter;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.resource = resource;
    }

    public void setBounds(LatLngBounds bounds) {
        latLngBounds = bounds;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();

                if (constraint == null)
                    return results; //skip filtering if no constraint given

                suggestions = getSuggestions(constraint);

                if (suggestions != null) {
                    results.values = suggestions;
                    results.count = suggestions.size();
                }

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

                if (results != null && results.count > 0)
                    notifyDataSetChanged();
                else
                    notifyDataSetInvalidated();
            }
        };
    }

    @Override
    public PlaceSuggestion getItem(int position) {
        return suggestions.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        TextView text;

        if (convertView == null) {
            view = inflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        try {
            if (fieldId == 0) {
                //  If no custom field is assigned, assume the whole resource is a TextView
                text = (TextView) view;
            } else {
                //  Otherwise, find the TextView field within the layout
                text = (TextView) view.findViewById(fieldId);
            }
        } catch (ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "ArrayAdapter requires the resource ID to be a TextView", e);
        }

        PlaceSuggestion item = getItem(position);
        List<? extends AutocompletePrediction.Substring> matches = item.matchedSubstrings;
        Spannable styledText = new SpannableStringBuilder(item.description);

        for (AutocompletePrediction.Substring match : matches) {
            int start = match.getOffset();
            int end = start + match.getLength();
            styledText.setSpan(new ForegroundColorSpan(Color.argb(138, 0, 0, 0)), start, end, 0);
        }

        text.setText(styledText);

        return view;
    }

    @Override
    public int getCount() {
        return suggestions.size();
    }

    public ArrayList<PlaceSuggestion> getSuggestions(CharSequence constraint) {

        //random fake suggestions for testing
        /*
        final CharSequence str = constraint;
        ArrayList<PlaceSuggestion> a = new ArrayList<>();
        AutocompletePrediction.Substring s = new AutocompletePrediction.Substring() {
            @Override
            public int getOffset() {
                return 0;
            }

            @Override
            public int getLength() {
                return str.length();
            }
        };
        List<AutocompletePrediction.Substring> list = new ArrayList<>();
        list.add(s);
        for (int i = 0; i < 10; i++) {
            a.add(new PlaceSuggestion(Integer.toString(i), constraint.toString() + (int)(Math.random()*100), list));
        }
        return a;
        */

        ///*
        if (!googleApiClient.isConnected()) {
            Log.d(MainActivity.TAG, "Google API client not connected");
            return null;
        }

        PendingResult<AutocompletePredictionBuffer> results = Places.GeoDataApi.getAutocompletePredictions(googleApiClient, constraint.toString(), latLngBounds, autoCompFilter);
        AutocompletePredictionBuffer buffer = results.await(30, TimeUnit.SECONDS);

        //load failure
        if (!buffer.getStatus().isSuccess()) {
            Toast.makeText(getContext(), "Could not load suggestions from server.", Toast.LENGTH_SHORT).show();
            Log.e(MainActivity.TAG, "places API autocomplete load error: " + buffer.getStatus().getStatusMessage());
            buffer.release();
            return null;
        }

        Iterator<AutocompletePrediction> iterator = buffer.iterator();
        ArrayList<PlaceSuggestion> suggestionsList = new ArrayList<PlaceSuggestion>();

        //load data from autocomplete buffer into list
        while (iterator.hasNext()) {
            AutocompletePrediction prediction = iterator.next();
            suggestionsList.add(new PlaceSuggestion(prediction.getPlaceId(), prediction.getDescription(), prediction.getMatchedSubstrings()));
        }

        buffer.release();
        return suggestionsList;
        //*/
    }

    public class PlaceSuggestion {
        String placeId;
        String description;
        List<? extends AutocompletePrediction.Substring> matchedSubstrings;

        public PlaceSuggestion(String placeId, String description, List<? extends AutocompletePrediction.Substring> matchedSubstrings) {
            this.placeId = placeId;
            this.description = description;
            this.matchedSubstrings = matchedSubstrings;
        }

        public String toString() {
            return description;
        }
    }
}
