package com.litesplash.photomap;

import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Created by Eugene on 7/23/2015.
 */
public class Util {

    public static int dpToPx(int dp, DisplayMetrics dm) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);
    }

}
