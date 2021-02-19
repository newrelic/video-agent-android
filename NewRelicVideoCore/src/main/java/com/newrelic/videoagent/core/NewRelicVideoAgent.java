package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.model.NRTrackerPair;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NewRelicVideoAgent {

    private static NewRelicVideoAgent singleton = null;

    private Map<Integer, NRTrackerPair> trackerPairs;
    private Integer trackerIdIndex;
    private String uuid;

    public static NewRelicVideoAgent getInstance() {
        if (singleton == null) {
            singleton = new NewRelicVideoAgent();
        }

        return singleton;
    }

    private NewRelicVideoAgent() {
        trackerIdIndex = 0;
        uuid = UUID.randomUUID().toString();
        trackerPairs = new HashMap<>();
    }

    public String getSessionId() {
        return uuid;
    }

    public Integer start(NRTracker contentTracker) {
        return start(contentTracker, null);
    }

    public Integer start(NRTracker contentTracker, NRTracker adTracker) {
        NRTrackerPair pair = new NRTrackerPair(contentTracker, adTracker);
        Integer trackerId = trackerIdIndex++;
        trackerPairs.put(trackerId, pair);

        if (adTracker instanceof NRVideoTracker) {
            ((NRVideoTracker)adTracker).getState().isAd = true;
        }

        if (contentTracker != null && adTracker != null) {
            contentTracker.linkedTracker = adTracker;
            adTracker.linkedTracker = contentTracker;
        }

        if (contentTracker != null) {
            contentTracker.trackerReady();
        }

        if (adTracker != null) {
            adTracker.trackerReady();
        }

        return trackerId;
    }

    public void releaseTracker(Integer trackerId) {
        if (getContentTracker(trackerId) != null) {
            getContentTracker(trackerId).dispose();
        }
        if (getAdTracker(trackerId) != null) {
            getAdTracker(trackerId).dispose();
        }

        trackerPairs.remove(trackerId);
    }

    public NRTracker getContentTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        return pair.getFirst();
    }

    public NRTracker getAdTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        return pair.getSecond();
    }
}
