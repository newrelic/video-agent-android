package com.newrelic.videoagent.core.mediatailor.model;

/**
 * Data Transfer Object representing MediaTailor session initialization response.
 * Contains the manifest URL for playback and tracking URL for ad schedule data.
 */
public class MediaTailorSession {
    private final String manifestUrl;
    private final String trackingUrl;

    public MediaTailorSession(String manifestUrl, String trackingUrl) {
        this.manifestUrl = manifestUrl;
        this.trackingUrl = trackingUrl;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    @Override
    public String toString() {
        return "MediaTailorSession{" +
                "manifestUrl='" + manifestUrl + '\'' +
                ", trackingUrl='" + trackingUrl + '\'' +
                '}';
    }
}
