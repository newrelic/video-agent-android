package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Data Transfer Object representing the root tracking response from MediaTailor API.
 * Contains a list of avails (ad breaks) with their associated ads and tracking events.
 */
public class MediaTailorTrackingData {
    private final List<Avail> avails;

    public MediaTailorTrackingData(List<Avail> avails) {
        this.avails = avails;
    }

    public List<Avail> getAvails() {
        return avails;
    }

    @Override
    public String toString() {
        return "MediaTailorTrackingData{" +
                "avails=" + (avails != null ? avails.size() : 0) + " avails" +
                '}';
    }
}
