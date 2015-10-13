package com.litesplash.picmapper;

import android.test.ActivityInstrumentationTestCase2;

/**
 * Created by eugene on 9/1/15.
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mainActivity;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mainActivity = getActivity();
    }
}
