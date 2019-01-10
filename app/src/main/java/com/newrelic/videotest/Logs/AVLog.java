package com.newrelic.videotest.Logs;

import android.util.Log;

public class AVLog {

    enum LOG_TYPE  {
        LOG_TYPE_CONSOLE,
        LOG_TYPE_FILE
    }

    private static LOG_TYPE logType = LOG_TYPE.LOG_TYPE_CONSOLE;

    public static void setLogType(LOG_TYPE type) {
        logType = type;
    }

    public static void out(String str) {

        String logStr = "(" + Long.toString(System.currentTimeMillis()) + "): " + str;

        if (logType == LOG_TYPE.LOG_TYPE_CONSOLE) {
            Log.v("NRLog", logStr);
        }
        else if (logType == LOG_TYPE.LOG_TYPE_FILE) {
            // TODO: file log
        }
    }
}
