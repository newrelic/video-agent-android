package com.newrelic.videoagent.jni;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Pair;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.videoagent.EventDefs;
import com.newrelic.videoagent.NRLog;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CAL {
    private static CAL instance = null;

    private Map<Long, Map<String, Pair<Object, Method>>> callbacksTree;
    private ScheduledExecutorService scheduler;
    private long trackerPointer;

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // NOTE: we MUST run it in the main thread, because the JNIEnv pointer we stored is the main thread one and in Java JNI every thread has ts own JNIEnv.
            // Mixing up different JNIEnv instances causes a crash.
            new Handler(Looper.getMainLooper()).post(new Runnable () {
                @Override
                public void run () {
                    callTrackerTimeEvent(trackerPointer);
                }
            });
        }
    };

    public static native void callTrackerTimeEvent(long trackerPointer);

    protected CAL() {
        callbacksTree = new HashMap<>();
    }

    public static CAL getInstance() {
        if (instance == null) {
            instance = new CAL();
        }
        return instance;
    }

    public static void recordCustomEvent(String name, Map attr) {
        if (NewRelic.currentSessionId() != null) {
            if (attr == null) {
                attr = new HashMap();
            }
            attr.put("actionName", name);
            NewRelic.recordCustomEvent(EventDefs.VIDEO_EVENT, attr);
        }
        else {
            NRLog.e("⚠️ The NewRelicAgent is not initialized, you need to do it before using the NewRelicVideo. ⚠️");
        }
    }

    public static String currentSessionId() {
        if (NewRelic.currentSessionId() != null) {
            return NewRelic.currentSessionId();
        }
        else {
            return "";
        }
    }

    public static double systemTimestamp() {
        return (double)System.currentTimeMillis() / 1000.0f;
    }

    public static void AV_LOG(String str) {
        NRLog.d(str);
    }

    @Nullable
    public static Object callGetter(String name, Long pointerToOrigin) {

        Map<String, Pair<Object, Method>> callbacks = getInstance().callbacksTree.get(pointerToOrigin);

        if (callbacks != null) {

            Object ret;

            Pair<Object, Method> pair = callbacks.get(name);

            if (pair != null) {
                Object target = pair.first;
                Method method = pair.second;
                try {
                    ret = method.invoke(target);
                }
                catch (Exception e) {
                    NRLog.e("Error calling a getter with name " + name + " = " + e);
                    return null;
                }
            }
            else {
                return null;
            }

            return ret;
        }
        else {
            return null;
        }
    }

    public static void registerGetter(String name, Object target, Method method, Long pointerToOrigin) {

        // Get callbacks for object with address "pointerToOrigin"
        Map<String, Pair<Object, Method>> callbacks = getInstance().callbacksTree.get(pointerToOrigin);

        // If no callbacks yet, create an empty hashmap
        if (callbacks == null) {
            getInstance().callbacksTree.put(pointerToOrigin, new HashMap<String, Pair<Object, Method>>());
            callbacks = getInstance().callbacksTree.get(pointerToOrigin);
        }

        // Store the new callback indexed by "name" (getter name)
        callbacks.put(name, new Pair<>(target, method));
    }

    public static void startTimer(long trackerPointer, double timeInterval) {
        getInstance().trackerPointer = trackerPointer;

        if (getInstance().scheduler == null) {
            getInstance().scheduler = Executors.newScheduledThreadPool(1);
        }

        getInstance().scheduler.scheduleAtFixedRate(getInstance().runnable, (long)timeInterval, (long)timeInterval, TimeUnit.SECONDS);
    }

    public static void abortTimer() {
        if (getInstance().scheduler != null) {
            getInstance().scheduler.shutdown();
        }
    }
}
