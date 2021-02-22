package com.newrelic.videoagent.core.utils;

import android.util.Log;

/**
 * `NRLog` contains methods for logging.
 */
public class NRLog {
    private static boolean logging = false;

    /**
     * Print debug message.
     *
     * @param s Message.
     */
    public static void d(String s) {
        if (logging) {
            Log.v("NewRelicVideo", s);
        }
    }

    /**
     * Print error message.
     *
     * @param s Message.
     */
    public static void e(String s) {
        if (logging) {
            Log.e("NewRelicVideo", s);
        }
    }

    /**
     * Enable logging.
     */
    public static void enable() {
        logging = true;
    }

    /**
     * Disable logging.
     */
    public static void disable() {
        logging = false;
    }

    private NRLog() {}
}
