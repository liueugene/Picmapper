package com.litesplash.picmapper;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Created by Eugene on 8/9/2015.
 */
public class SlideUpPanelLayout extends RelativeLayout {

    private static final String LOG_TAG = "SlideUpPanelLayout";

    private static final String PANEL_FRAGMENT = "panelFragment";

    private View backgroundView;
    private View panelView;

    private int panelTop;
    private int panelTopBound;
    private int panelHeight;

    private GradientDrawable shadowDrawable;
    private int shadowBottomBound;
    private int shadowHeight;
    private int[] shadowColors = {0x55000000, 0x00000000};

    private int backgroundViewResId;
    private int panelViewResId;

    private ViewDragHelper dragHelper;

    private boolean touchStartedOnPanel;
    private boolean panelActive;

    private float closeThreshold = 0.4f; //minimum percentage that the panel is scrolled down by before it can be closed

    private FragmentManager fragmentManager;

    private Listener listener;

    public SlideUpPanelLayout(Context context) {
        this(context, null, 0);
    }

    public SlideUpPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlideUpPanelLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlideUpPanelLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlideUpPanelLayout, defStyleAttr, defStyleRes);
            backgroundViewResId = a.getResourceId(R.styleable.SlideUpPanelLayout_backgroundView, 0);
            panelViewResId = a.getResourceId(R.styleable.SlideUpPanelLayout_panelView, 0);
        }

        dragHelper = ViewDragHelper.create(this, new ViewDragHelperCallback());
        shadowHeight = Util.dpToPx(8, getResources().getDisplayMetrics());
        shadowDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, shadowColors);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        backgroundView = findViewById(backgroundViewResId);
        panelView = findViewById(panelViewResId);

        Log.d(LOG_TAG, "background view: " + backgroundView.toString());
        Log.d(LOG_TAG, "panel view: " + panelView.toString());
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void setBackgroundFragment(Fragment fragment) {
        fragmentManager.beginTransaction().replace(backgroundViewResId, fragment).commit();
    }

    public void setPanelFragment(Fragment fragment) {
        removePanelFragment();
        fragmentManager.beginTransaction()
                .add(panelViewResId, fragment)
                .addToBackStack(PANEL_FRAGMENT)
                .commit();
        panelActive = true;
    }

    public void removePanelFragment() {
        fragmentManager.popBackStackImmediate(PANEL_FRAGMENT, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        panelActive = false;
    }

    public void setBackgroundView(View view) {
        backgroundView = view;
        backgroundViewResId = view.getId();
    }

    public void setPanelView(View view) {
        panelView = view;
        panelViewResId = view.getId();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void removeListener() {
        listener = null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return isPanelTarget(event) && dragHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return true;//super.onTouchEvent(event);
    }

    private boolean isPanelTarget(MotionEvent event) {
        if (!panelActive)
            return false;

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            return touchStartedOnPanel;
        }

        //update panel height if changed
        int curHeight = panelView.getHeight();
        if (curHeight != panelHeight) {
            panelHeight = curHeight;
            panelTopBound = getBottom() - panelHeight;
        }

        int y = (int) event.getRawY();
        boolean touchWithinBounds = y > panelTopBound;

        if (action == MotionEvent.ACTION_DOWN) {
            touchStartedOnPanel = touchWithinBounds;

        } else if (action == MotionEvent.ACTION_MOVE) {
            return touchStartedOnPanel && touchWithinBounds;
        }

        return touchStartedOnPanel;
    }


    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(SlideUpPanelLayout.this);
        }
    }

    private class ViewDragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == panelView;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return Math.max(top, panelTopBound);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return child.getHeight();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int top = releasedChild.getTop();
            Log.d(LOG_TAG, "yvel: " + yvel);
            Log.d(LOG_TAG, "top: " + top);

            if (yvel >= 4000 || top >= panelTopBound + closeThreshold * panelHeight) {
                dragHelper.settleCapturedViewAt(releasedChild.getLeft(), getBottom());
            } else {
                dragHelper.settleCapturedViewAt(releasedChild.getLeft(), panelTopBound);
            }

            ViewCompat.postInvalidateOnAnimation(SlideUpPanelLayout.this);
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            panelTop = top;
            ViewCompat.postInvalidateOnAnimation(SlideUpPanelLayout.this);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (listener != null && state == ViewDragHelper.STATE_IDLE && panelTop == getBottom()) {
                Log.d(LOG_TAG, "onPanelClosed called");
                listener.onPanelClosed(panelView);
            }
        }
    }

    public interface Listener {
        void onPanelClosed(View panelView);
    }
}
