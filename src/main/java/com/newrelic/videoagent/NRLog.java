package com.newrelic.videoagent;

import android.util.Log;

public class NRLog {

    private static final String TAG = NewRelicVideoAgent.class.getPackage().getName();
    private static Boolean flag = true;

    public static void enable() {
        flag = true;
    }

    public static void disable() {
        flag = false;
    }

    public static void d(String str) {
        if (flag) {
            Log.d(TAG, out(str));
        }
    }

    public static void e(String str) {
        if (flag) {
            Log.e(TAG, out(str));
        }
    }

    private static String out(String str) {
        return "NRLog(" + Long.toString(System.currentTimeMillis()) + "): " + str;
    }
}
