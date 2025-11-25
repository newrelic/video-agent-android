package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Data Transfer Object representing an individual ad from MediaTailor API.
 * Part of an Avail (ad break) containing timing and tracking event information.
 */
public class Ad {
    private final String adId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String duration;
    private final List<TrackingEvent> trackingEvents;

    public Ad(String adId, double startTimeInSeconds, double durationInSeconds,
              String duration, List<TrackingEvent> trackingEvents) {
        this.adId = adId;
        this.startTimeInSeconds = startTimeInSeconds;
        this.durationInSeconds = durationInSeconds;
        this.duration = duration;
        this.trackingEvents = trackingEvents;
    }

    public String getAdId() {
        return adId;
    }

    public double getStartTimeInSeconds() {
        return startTimeInSeconds;
    }

    public double getDurationInSeconds() {
        return durationInSeconds;
    }

    public String getDuration() {
        return duration;
    }

    public List<TrackingEvent> getTrackingEvents() {
        return trackingEvents;
    }

    @Override
    public String toString() {
        return "Ad{" +
                "adId='" + adId + '\'' +
                ", startTimeInSeconds=" + startTimeInSeconds +
                ", durationInSeconds=" + durationInSeconds +
                ", duration='" + duration + '\'' +
                ", trackingEvents=" + trackingEvents.size() + " events" +
                '}';
    }
}
