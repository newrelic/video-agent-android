package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Data Transfer Object representing a tracking event from MediaTailor API.
 * Contains beacon URLs and timing information for ad tracking events.
 */
public class TrackingEvent {
    private final String eventId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String eventType;
    private final List<String> beaconUrls;

    public TrackingEvent(String eventId, double startTimeInSeconds, double durationInSeconds,
                        String eventType, List<String> beaconUrls) {
        this.eventId = eventId;
        this.startTimeInSeconds = startTimeInSeconds;
        this.durationInSeconds = durationInSeconds;
        this.eventType = eventType;
        this.beaconUrls = beaconUrls;
    }

    public String getEventId() {
        return eventId;
    }

    public double getStartTimeInSeconds() {
        return startTimeInSeconds;
    }

    public double getDurationInSeconds() {
        return durationInSeconds;
    }

    public String getEventType() {
        return eventType;
    }

    public List<String> getBeaconUrls() {
        return beaconUrls;
    }

    @Override
    public String toString() {
        return "TrackingEvent{" +
                "eventId='" + eventId + '\'' +
                ", startTimeInSeconds=" + startTimeInSeconds +
                ", durationInSeconds=" + durationInSeconds +
                ", eventType='" + eventType + '\'' +
                ", beaconUrls=" + beaconUrls +
                '}';
    }
}
