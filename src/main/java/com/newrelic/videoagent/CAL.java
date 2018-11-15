package com.newrelic.videoagent;

import android.util.Pair;
import com.newrelic.agent.android.NewRelic;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CAL {

    private Map<Long, Map<String, Pair<Object, Method>>> callbacksTree;

    private static CAL instance = null;

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

    public static Object callGetter(String name, Long pointerToOrigin) {

        // TODO: find callback, execute and return result

        Object target = null;
        Method method = null;

        try {
            Object ret = method.invoke(target);
        }
        catch (Exception e) { }

        return null;
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
}
