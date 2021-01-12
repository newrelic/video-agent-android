package com.newrelic.videoagent.core.utils;

import android.util.Log;

public class NRLog {
    private static boolean logging = false;

    public static void d(String s) {
        if (logging) {
            Log.v("NewRelicVideo", s);
        }
    }

    public static void e(String s) {
        if (logging) {
            Log.e("NewRelicVideo", s);
        }
    }

    public static void enable() {
        logging = true;
    }

    public static void disable() {
        logging = false;
    }

    private NRLog() {}
}
