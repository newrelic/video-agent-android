package com.newrelic.videoagent.utils;

import android.util.Log;

public class NRLog {

    private static String TAG = NRLog.class.getPackage().getName();

    // TODO: for "d" check for a config flag (where??), just like plist in iOS.

    public static void d(String str) {
        Log.d(TAG, out(str));
    }

    public static void e(String str) {
        Log.e(TAG, out(str));
    }

    private static String out(String str) {
        return "(" + Long.toString(System.currentTimeMillis()) + "): " + str;
    }
}
