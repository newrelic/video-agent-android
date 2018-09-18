package com.newrelic.videoagent;

import android.util.Log;

public class AVLog {

    private static String TAG = AVLog.class.getPackage().getName();

    // TODO: for "i", "d" and "v" check for a config flag (where??), just like plist in iOS.

    public static void i(String str) {
        Log.i(TAG, out(str));
    }

    public static void v(String str) {
        Log.v(TAG, out(str));
    }

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
