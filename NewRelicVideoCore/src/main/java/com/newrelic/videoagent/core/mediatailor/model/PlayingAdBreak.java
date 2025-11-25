package com.newrelic.videoagent.core.mediatailor.model;

/**
 * State object representing the currently playing ad break and ad.
 * Used to track which ad is currently being played within an ad break.
 */
public class PlayingAdBreak {
    private final MediaTailorAdBreak adBreak;
    private final int adIndex;

    public PlayingAdBreak(MediaTailorAdBreak adBreak, int adIndex) {
        this.adBreak = adBreak;
        this.adIndex = adIndex;
    }

    public MediaTailorAdBreak getAdBreak() {
        return adBreak;
    }

    public int getAdIndex() {
        return adIndex;
    }

    /**
     * Get the currently playing ad.
     * @return The ad at the current index
     */
    public MediaTailorLinearAd getAd() {
        if (adBreak != null && adBreak.getAds() != null &&
            adIndex >= 0 && adIndex < adBreak.getAds().size()) {
            return adBreak.getAds().get(adIndex);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlayingAdBreak that = (PlayingAdBreak) o;

        if (adIndex != that.adIndex) return false;
        return adBreak != null ? adBreak.getId().equals(that.adBreak.getId()) : that.adBreak == null;
    }

    @Override
    public int hashCode() {
        int result = adBreak != null ? adBreak.getId().hashCode() : 0;
        result = 31 * result + adIndex;
        return result;
    }

    @Override
    public String toString() {
        return "PlayingAdBreak{" +
                "adBreakId='" + (adBreak != null ? adBreak.getId() : "null") + '\'' +
                ", adIndex=" + adIndex +
                ", adId='" + (getAd() != null ? getAd().getId() : "null") + '\'' +
                '}';
    }
}
