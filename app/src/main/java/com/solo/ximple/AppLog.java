package com.solo.ximple;

import android.util.Log;
public class AppLog {

    private static String TAG = "solo_ProxyLogger";

    public static void D(String s) {
         Log.d(TAG, "D: " + s);
    }

    public static void E(String s) {
        Log.e(TAG, "E: " + s);
    }

}
