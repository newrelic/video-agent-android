package com.newrelic.videoagent.mediatailor.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.newrelic.videoagent.core.NRAdConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class NRTrackerMediaTailorStreamTypeTest {

    @Test
    public void adEventsCarrySsaiMtAndContentEventsDoNot() {
        NRTrackerMediaTailor tracker = new NRTrackerMediaTailor(null, NRAdConfig.mediaTailor());
        try {
            for (String action : new String[]{"AD_START", "AD_END", "AD_ERROR", "AD_BREAK_START"}) {
                Map<String, Object> attrs = tracker.getAttributes(action, new HashMap<String, Object>());
                assertEquals(action, "SSAI_MT", attrs.get("adStreamType"));
            }
            Map<String, Object> content = tracker.getAttributes("CONTENT_START", new HashMap<String, Object>());
            assertFalse(content.containsKey("adStreamType"));
        } finally {
            tracker.dispose();
        }
    }
}
