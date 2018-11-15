package com.newrelic.videoagent;

import com.newrelic.agent.android.NewRelic;
import java.util.Map;

public class CAL {

    public static void hello() {
        NRLog.d("hello from CAL");
    }

    public static void recordCustomEvent(String name, Map attr) {
        if (NewRelic.currentSessionId() != null) {
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
}
