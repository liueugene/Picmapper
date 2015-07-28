package com.litesplash.photomap;

import android.content.Context;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

/**
 * Created by Eugene on 7/23/2015.
 */
public class CustomClusterRenderer<T extends ClusterItem> extends DefaultClusterRenderer<T> {

    private T lastActiveMarker;
    private boolean lastActiveMarkerRendered;
    private OnLastActiveMarkerRenderedListener listener;

    public CustomClusterRenderer(Context context, GoogleMap map, ClusterManager clusterManager, T lastActiveMarker, OnLastActiveMarkerRenderedListener listener) {
        super(context, map, clusterManager);

        if (lastActiveMarker == null) {
            lastActiveMarkerRendered = true;
        } else {
            this.lastActiveMarker = lastActiveMarker;
            lastActiveMarkerRendered = false;
        }

        this.listener = listener;
    }

    @Override
    protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
        return cluster.getSize() >= 2;
    }

    @Override
    protected void onClusterItemRendered(T clusterItem, Marker marker) {
        if (lastActiveMarkerRendered)
            return;

        if (clusterItem.equals(lastActiveMarker)) {
            listener.onLastActiveMarkerRendered(clusterItem, marker);
            lastActiveMarkerRendered = true;
        }
    }

    public interface OnLastActiveMarkerRenderedListener<T extends ClusterItem> {
        void onLastActiveMarkerRendered(T clusterItem, Marker marker);
    }
}
