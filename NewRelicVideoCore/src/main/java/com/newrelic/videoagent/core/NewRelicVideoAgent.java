package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.model.NRTrackerPair;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * `NewRelicVideoAgent` contains the methods to start the Video Agent and access tracker instances.
 */
public class NewRelicVideoAgent {

    private static NewRelicVideoAgent singleton = null;

    private Map<Integer, NRTrackerPair> trackerPairs;
    private Integer trackerIdIndex;
    private String uuid;

    /**
     * Get shared instance.
     *
     * @return Singleton.
     */
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

    /**
     * Get session ID.
     *
     * @return Session ID.
     */
    public String getSessionId() {
        return uuid;
    }

    /**
     * Start a content tracker.
     *
     * @param contentTracker Tracker instance for contents.
     * @return Tracker ID.
     */
    public Integer start(NRTracker contentTracker) {
        return start(contentTracker, null);
    }

    /**
     * Start a content and an ads tracker.
     *
     * @param contentTracker Tracker instance for contents.
     * @param adTracker Tracker instance for ads.
     * @return Tracker ID.
     */
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

    /**
     * Release a tracker.
     *
     * @param trackerId Tracker ID.
     */
    public void releaseTracker(Integer trackerId) {
        if (getContentTracker(trackerId) != null) {
            getContentTracker(trackerId).dispose();
        }
        if (getAdTracker(trackerId) != null) {
            getAdTracker(trackerId).dispose();
        }

        trackerPairs.remove(trackerId);
    }

    /**
     * Get content tracker.
     *
     * @param trackerId Tracker ID.
     * @return Content tracker.
     */
    public NRTracker getContentTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        if (pair != null) {
            return pair.getFirst();
        }
        else {
            return null;
        }
    }

    /**
     * Get ad tracker.
     *
     * @param trackerId Tracker ID.
     * @return Content tracker.
     */
    public NRTracker getAdTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        if (pair != null) {
            return pair.getSecond();
        }
        else {
            return null;
        }
    }
}
