package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Domain model representing an ad break (avail) with scheduled ads.
 * Contains timing information and a list of linear ads within this break.
 */
public class MediaTailorAdBreak {
    private final String id;
    private final List<MediaTailorLinearAd> ads;
    private final double scheduleTime;
    private final double duration;
    private final String formattedDuration;
    private final double adMarkerDuration;

    public MediaTailorAdBreak(String id, List<MediaTailorLinearAd> ads, double scheduleTime,
                             double duration, String formattedDuration, double adMarkerDuration) {
        this.id = id;
        this.ads = ads;
        this.scheduleTime = scheduleTime;
        this.duration = duration;
        this.formattedDuration = formattedDuration;
        this.adMarkerDuration = adMarkerDuration;
    }

    public String getId() {
        return id;
    }

    public List<MediaTailorLinearAd> getAds() {
        return ads;
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

    public double getAdMarkerDuration() {
        return adMarkerDuration;
    }

    /**
     * Get the end time of this ad break.
     * @return End time in seconds
     */
    public double getEndTime() {
        return scheduleTime + duration;
    }

    /**
     * Check if the given player time is within this ad break's time range.
     * @param playerTime Current player time in seconds
     * @return true if player time is within [scheduleTime, endTime)
     */
    public boolean isInRange(double playerTime) {
        return playerTime >= scheduleTime && playerTime < getEndTime();
    }

    @Override
    public String toString() {
        return "MediaTailorAdBreak{" +
                "id='" + id + '\'' +
                ", scheduleTime=" + scheduleTime +
                ", duration=" + duration +
                ", formattedDuration='" + formattedDuration + '\'' +
                ", adMarkerDuration=" + adMarkerDuration +
                ", ads=" + (ads != null ? ads.size() : 0) + " ads" +
                '}';
    }
}
