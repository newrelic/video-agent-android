package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Domain model representing a linear ad within an ad break.
 * Contains scheduling information and tracking events for the ad.
 */
public class MediaTailorLinearAd {
    private final String id;
    private final double scheduleTime;
    private final double duration;
    private final String formattedDuration;
    private final List<MediaTailorTrackingEvent> trackingEvents;

    public MediaTailorLinearAd(String id, double scheduleTime, double duration,
                              String formattedDuration, List<MediaTailorTrackingEvent> trackingEvents) {
        this.id = id;
        this.scheduleTime = scheduleTime;
        this.duration = duration;
        this.formattedDuration = formattedDuration;
        this.trackingEvents = trackingEvents;
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

    public String getFormattedDuration() {
        return formattedDuration;
    }

    public List<MediaTailorTrackingEvent> getTrackingEvents() {
        return trackingEvents;
    }

    /**
     * Get the end time of this ad.
     * @return End time in seconds
     */
    public double getEndTime() {
        return scheduleTime + duration;
    }

    /**
     * Check if the given player time is within this ad's time range.
     * @param playerTime Current player time in seconds
     * @return true if player time is within [scheduleTime, endTime)
     */
    public boolean isInRange(double playerTime) {
        return playerTime >= scheduleTime && playerTime < getEndTime();
    }

    @Override
    public String toString() {
        return "MediaTailorLinearAd{" +
                "id='" + id + '\'' +
                ", scheduleTime=" + scheduleTime +
                ", duration=" + duration +
                ", formattedDuration='" + formattedDuration + '\'' +
                ", trackingEvents=" + (trackingEvents != null ? trackingEvents.size() : 0) + " events" +
                '}';
    }
}
