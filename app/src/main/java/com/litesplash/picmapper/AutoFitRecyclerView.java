package com.litesplash.picmapper;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

/**
 * Created by Eugene on 7/26/2015.
 */
public class AutoFitRecyclerView extends RecyclerView {

    private int maxItemWidth;
    private GridLayoutManager layoutManager;

    public AutoFitRecyclerView(Context context) {
        this(context, null, 0);
    }

    public AutoFitRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        maxItemWidth = getResources().getDimensionPixelSize(R.dimen.photo_grid_item_size);
        layoutManager = new GridLayoutManager(context, 1);
        setLayoutManager(layoutManager);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        int spanCount = Math.max(1, getMeasuredWidth() / maxItemWidth);
        layoutManager.setSpanCount(spanCount);
    }
}
