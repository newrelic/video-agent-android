package com.newrelic.videoagent.core.utils;

import android.util.Log;
import java.util.function.Supplier;

/**
 * `NRLog` contains methods for logging.
 */
public class NRLog {
    private static final String TAG = "NRVideo";
    private static boolean logging = false;
    // null means no filtering (all levels, including debug)
    private static volatile NRLogLevel minLevel = null;

    /**
     * Print debug message.
     *
     * @param s Message.
     */
    public static void d(String s) {
        if (logging && minLevel == null) {
            Log.d(TAG, s);
        }
    }

    /**
     * Print debug message using a supplier — the string is only built if logging is enabled.
     * Use this instead of d(String) when building the message is expensive (e.g. Map.toString()).
     *
     * @param supplier Message supplier.
     */
    public static void d(Supplier<String> supplier) {
        if (logging && minLevel == null) {
            Log.d(TAG, supplier.get());
        }
    }

    /**
     * Print info message.
     *
     * @param s Message.
     */
    public static void i(String s) {
        if (logging && isLevelEnabled(NRLogLevel.INFO)) {
            Log.i(TAG, s);
        }
    }

    /**
     * Print error message.
     *
     * @param s Message.
     */
    public static void e(String s) {
        if (logging && isLevelEnabled(NRLogLevel.ERROR)) {
            Log.e(TAG, s);
        }
    }

    /**
     * Print error message.
     *
     * @param s Message.
     */
    public static void e(String s, Exception e) {
        if (logging && isLevelEnabled(NRLogLevel.ERROR)) {
            Log.e(TAG, s, e);
        }
    }

    /**
     * Print warning message.
     *
     * @param s Message.
     */
    public static void w(String s) {
        if (logging && isLevelEnabled(NRLogLevel.WARNING)) {
            Log.w(TAG, s);
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

    /**
     * Set the minimum level to log. Debug messages are suppressed while a level is set.
     *
     * @param level Minimum level, or null to log everything.
     */
    public static void setLevel(NRLogLevel level) {
        minLevel = level;
    }

    private static boolean isLevelEnabled(NRLogLevel level) {
        NRLogLevel min = minLevel;
        return min == null || level.ordinal() >= min.ordinal();
    }

    private NRLog() {}
}
