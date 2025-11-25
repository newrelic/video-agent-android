package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Domain model representing a tracking event for ad beacons.
 * Used for firing tracking URLs at specific times during ad playback.
 */
public class MediaTailorTrackingEvent {
    private final String id;
    private final double scheduleTime;
    private final double duration;
    private final String eventType;
    private final List<String> beaconUrls;

    public MediaTailorTrackingEvent(String id, double scheduleTime, double duration,
                                   String eventType, List<String> beaconUrls) {
        this.id = id;
        this.scheduleTime = scheduleTime;
        this.duration = duration;
        this.eventType = eventType;
        this.beaconUrls = beaconUrls;
    }

    public String getId() {
        return id;
    }

    public double getScheduleTime() {
        return scheduleTime;
    }

    public double getDuration() {
        return duration;
    }

    public String getEventType() {
        return eventType;
    }

    public List<String> getBeaconUrls() {
        return beaconUrls;
    }

    @Override
    public String toString() {
        return "MediaTailorTrackingEvent{" +
                "id='" + id + '\'' +
                ", scheduleTime=" + scheduleTime +
                ", duration=" + duration +
                ", eventType='" + eventType + '\'' +
                ", beaconUrls=" + (beaconUrls != null ? beaconUrls.size() : 0) + " urls" +
                '}';
    }
}
