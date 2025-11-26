package com.newrelic.videoagent.core.mediatailor.model;

/**
 * Base class for MediaTailor ad lifecycle events.
 * Uses sealed class pattern (manual implementation for older Java versions).
 */
public abstract class MediaTailorEvent {

    /**
     * Get the event type name.
     * @return Event type string
     */
    public abstract String getEventType();

    /**
     * Get formatted event information.
     * @return Event details as string
     */
    public abstract String getEventInfo();

    /**
     * Event fired when an ad break starts.
     */
    public static class AdBreakStarted extends MediaTailorEvent {
        private final MediaTailorAdBreak adBreak;

        public AdBreakStarted(MediaTailorAdBreak adBreak) {
            this.adBreak = adBreak;
        }

        public MediaTailorAdBreak getAdBreak() {
            return adBreak;
        }

        @Override
        public String getEventType() {
            return "AD_BREAK_STARTED";
        }

        @Override
        public String getEventInfo() {
            return String.format("AdBreak[id=%s, scheduleTime=%.1fs, duration=%.1fs, adCount=%d]",
                    adBreak.getId(),
                    adBreak.getScheduleTime(),
                    adBreak.getDuration(),
                    adBreak.getAds() != null ? adBreak.getAds().size() : 0);
        }

        @Override
        public String toString() {
            return getEventType() + ": " + getEventInfo();
        }
    }

    /**
     * Event fired when an ad break finishes.
     */
    public static class AdBreakFinished extends MediaTailorEvent {
        private final MediaTailorAdBreak adBreak;

        public AdBreakFinished(MediaTailorAdBreak adBreak) {
            this.adBreak = adBreak;
        }

        public MediaTailorAdBreak getAdBreak() {
            return adBreak;
        }

        @Override
        public String getEventType() {
            return "AD_BREAK_FINISHED";
        }

        @Override
        public String getEventInfo() {
            return String.format("AdBreak[id=%s, duration=%.1fs, totalAds=%d]",
                    adBreak.getId(),
                    adBreak.getDuration(),
                    adBreak.getAds() != null ? adBreak.getAds().size() : 0);
        }

        @Override
        public String toString() {
            return getEventType() + ": " + getEventInfo();
        }
    }

    /**
     * Event fired when an individual ad starts.
     */
    public static class AdStarted extends MediaTailorEvent {
        private final MediaTailorLinearAd ad;
        private final int indexInQueue;

        public AdStarted(MediaTailorLinearAd ad, int indexInQueue) {
            this.ad = ad;
            this.indexInQueue = indexInQueue;
        }

        public MediaTailorLinearAd getAd() {
            return ad;
        }

        public int getIndexInQueue() {
            return indexInQueue;
        }

        @Override
        public String getEventType() {
            return "AD_STARTED";
        }

        @Override
        public String getEventInfo() {
            return String.format("Ad[id=%s, index=%d, scheduleTime=%.1fs, duration=%.1fs]",
                    ad.getId(),
                    indexInQueue,
                    ad.getScheduleTime(),
                    ad.getDuration());
        }

        @Override
        public String toString() {
            return getEventType() + ": " + getEventInfo();
        }
    }

    /**
     * Event fired when an individual ad finishes.
     */
    public static class AdFinished extends MediaTailorEvent {
        private final MediaTailorLinearAd ad;

        public AdFinished(MediaTailorLinearAd ad) {
            this.ad = ad;
        }

        public MediaTailorLinearAd getAd() {
            return ad;
        }

        @Override
        public String getEventType() {
            return "AD_FINISHED";
        }

        @Override
        public String getEventInfo() {
            return String.format("Ad[id=%s, duration=%.1fs]",
                    ad.getId(),
                    ad.getDuration());
        }

        @Override
        public String toString() {
            return getEventType() + ": " + getEventInfo();
        }
    }
}
