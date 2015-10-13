package com.litesplash.picmapper;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.AutoCompleteTextView;

/**
 * Created by Eugene on 7/20/2015.
 */
public class DelayAutoCompleteTextView extends AutoCompleteTextView {

    private static final int TEXT_CHANGED = 0xABC;
    private static final int DEFAULT_DELAY = 500;

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            DelayAutoCompleteTextView.super.performFiltering((CharSequence) msg.obj, msg.arg1);
        }
    };

    private int delayInMs;

    public DelayAutoCompleteTextView(Context context) {
        super(context);
        setDelay(DEFAULT_DELAY);
    }

    public DelayAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDelay(DEFAULT_DELAY);
    }

    public DelayAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setDelay(DEFAULT_DELAY);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DelayAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setDelay(DEFAULT_DELAY);
    }

    public void setDelay(int ms) {
        delayInMs = ms;
    }

    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        handler.removeMessages(TEXT_CHANGED);
        handler.sendMessageDelayed(handler.obtainMessage(TEXT_CHANGED, keyCode, 0, text), delayInMs);
    }
}
