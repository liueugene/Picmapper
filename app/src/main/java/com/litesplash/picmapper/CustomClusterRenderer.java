package com.litesplash.picmapper;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.LruCache;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.lang.ref.WeakReference;

/**
 * Created by Eugene on 7/23/2015.
 */
public class CustomClusterRenderer<T extends PhotoItem> extends DefaultClusterRenderer<T> {
    private static final int BITMAP_LOADED = 1;
    private static final String PHOTO_ITEM = "PhotoItem";

    private GoogleMap map;
    private ClusterManager clusterManager;
    private Context context;
    private Handler handler;
    private LruCache<T, Bitmap> thumbCache;

    public CustomClusterRenderer(Context context, GoogleMap map, ClusterManager clusterManager, LruCache<T, Bitmap> thumbCache) {
        super(context, map, clusterManager);
        this.map = map;
        this.clusterManager = clusterManager;
        this.context = context;
        handler = new ThumbHandler<T>(this);
        this.thumbCache = thumbCache;
    }

    @Override
    protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
        return cluster.getSize() >= 2;
    }

    @Override
    protected void onBeforeClusterItemRendered(final T item, MarkerOptions markerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions);

        // render thumbnail if view within range
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        if (!bounds.contains(item.getPosition()))
            return;

        // check for existing thumbnail in cache, otherwise load from MediaStore
        Bitmap existingThumb = thumbCache.get(item);
        if (existingThumb == null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), item.getId(), MediaStore.Images.Thumbnails.MICRO_KIND, null);
                    Message msg = Message.obtain();
                    msg.what = BITMAP_LOADED;
                    msg.obj = thumb;
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(PHOTO_ITEM, item);
                    msg.setData(bundle);

                    handler.sendMessage(msg);
                }
            }).start();
        } else {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(existingThumb));
        }
    }

    public void onThumbnailLoaded(T item, Bitmap bitmap) {
        Marker m = getMarker(item);
        m.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));
        thumbCache.put(item, bitmap);
        //clusterManager.cluster();
    }

    private static class ThumbHandler<T extends PhotoItem> extends Handler {
        private WeakReference<CustomClusterRenderer<T>> rendererRef;

        ThumbHandler(CustomClusterRenderer<T> renderer) {
            this.rendererRef = new WeakReference<>(renderer);
        }

        @Override
        public void handleMessage(Message msg) {
            CustomClusterRenderer<T> renderer = rendererRef.get();
            if (renderer == null)
                return;

            Bundle bundle = msg.getData();
            T item = bundle.getParcelable(PHOTO_ITEM);

            Bitmap bitmap = (Bitmap) msg.obj;

            if (msg.what == BITMAP_LOADED) {
                renderer.onThumbnailLoaded(item, bitmap);
            }
        }
    }
}
