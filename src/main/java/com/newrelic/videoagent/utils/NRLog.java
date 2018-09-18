package com.newrelic.videoagent.utils;

import android.util.Log;

import com.newrelic.videoagent.NewRelicVideoAgent;

public class NRLog {

    private static String TAG = NewRelicVideoAgent.class.getPackage().getName();

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
