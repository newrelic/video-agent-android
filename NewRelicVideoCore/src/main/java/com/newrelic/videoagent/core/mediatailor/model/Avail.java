package com.newrelic.videoagent.core.mediatailor.model;

import java.util.List;

/**
 * Data Transfer Object representing an Avail (ad break) from MediaTailor API.
 * Contains timing information and a list of ads within this ad break.
 */
public class Avail {
    private final String availId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String duration;
    private final double adMarkerDuration;
    private final List<Ad> ads;

    public Avail(String availId, double startTimeInSeconds, double durationInSeconds,
                 String duration, double adMarkerDuration, List<Ad> ads) {
        this.availId = availId;
        this.startTimeInSeconds = startTimeInSeconds;
        this.durationInSeconds = durationInSeconds;
        this.duration = duration;
        this.adMarkerDuration = adMarkerDuration;
        this.ads = ads;
    }

    public String getAvailId() {
        return availId;
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

    public double getAdMarkerDuration() {
        return adMarkerDuration;
    }

    public List<Ad> getAds() {
        return ads;
    }

    @Override
    public String toString() {
        return "Avail{" +
                "availId='" + availId + '\'' +
                ", startTimeInSeconds=" + startTimeInSeconds +
                ", durationInSeconds=" + durationInSeconds +
                ", duration='" + duration + '\'' +
                ", adMarkerDuration=" + adMarkerDuration +
                ", ads=" + ads.size() + " ads" +
                '}';
    }
}
