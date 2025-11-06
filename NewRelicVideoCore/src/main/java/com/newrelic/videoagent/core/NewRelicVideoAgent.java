package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.model.NRTrackerPair;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * `NewRelicVideoAgent` contains the methods to start the Video Agent and access tracker instances.
 */
public class NewRelicVideoAgent {

    private static NewRelicVideoAgent singleton = null;

    private Map<Integer, NRTrackerPair> trackerPairs;
    private Integer trackerIdIndex;
    private String uuid;
    private final AtomicBoolean isTV = new AtomicBoolean(false);

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

    public void setTv() {
        isTV.set(true);
    }

    public boolean isTV() {
        return isTV.get();
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

    /**
     * Set userId.
     *
     * @param userId User Id.
     * @deprecated Please use {@link NRVideo#setUserId(String)}
     */
    @Deprecated
    public void setUserId(String userId) {
        for (Integer trackerId : trackerPairs.keySet()) {
            NRTrackerPair pair = trackerPairs.get(trackerId);
            if (pair.getFirst() != null) {
                pair.getFirst().setAttribute("enduser.id", userId);
            }
            if (pair.getSecond() != null) {
                pair.getSecond().setAttribute("enduser.id", userId);
            }
        }
    }

    /**
     * Sets an attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public void setAttribute(Integer trackerId, String key, Object value, String action) {
        NRTracker contentTracker = getContentTracker(trackerId);
        if (contentTracker != null) {
            contentTracker.setAttribute(key, value, action);
        }
    }

    /**
     * Sets an ad attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public void setAdAttribute(Integer trackerId, String key, Object value, String action) {
        NRTracker adTracker = getAdTracker(trackerId);
        if (adTracker != null) {
            adTracker.setAttribute(key, value, action);
        }
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public void setGlobalAttribute(String key, Object value, String action) {
        for (Integer trackerId : trackerPairs.keySet()) {
            NRTrackerPair pair = trackerPairs.get(trackerId);
            if (pair.getFirst() != null) {
                pair.getFirst().setAttribute(key, value, action);
            }
            if (pair.getSecond() != null) {
                pair.getSecond().setAttribute(key, value, action);
            }
        }
    }

    /**
     * Sets an attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setAttribute(Integer trackerId, String key, Object value) {
        NRTracker contentTracker = getContentTracker(trackerId);
        if (contentTracker != null) {
            contentTracker.setAttribute(key, value);
        }
    }

    /**
     * Sets an ad attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setAdAttribute(Integer trackerId, String key, Object value) {
        NRTracker adTracker = getAdTracker(trackerId);
        if (adTracker != null) {
            adTracker.setAttribute(key, value);
        }
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setGlobalAttribute(String key, Object value) {
        for (Integer trackerId : trackerPairs.keySet()) {
            NRTrackerPair pair = trackerPairs.get(trackerId);
            if (pair.getFirst() != null) {
                pair.getFirst().setAttribute(key, value);
            }
            if (pair.getSecond() != null) {
                pair.getSecond().setAttribute(key, value);
            }
        }
    }


}
