package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.ima.BuildConfig;
import static com.newrelic.videoagent.core.NRDef.*;

public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private String adPosition = null;
    private String creativeId = null;
    private Long quartile = null;
    private String title = null;
    private Long bitrate = null;
    private Long renditionHeight = null;
    private Long renditionWidth = null;
    private Long duration = null;

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (adErrorEvent == null) {
            NRLog.d("NRTrackerIMA: onAdError received a null AdErrorEvent."); // Updated log call
            return;
        }

        NRLog.d("NRTrackerIMA: AdErrorEvent = " + adErrorEvent.getError().getMessage()); // Updated log call
        // Assuming sendError(Exception) in NRVideoTracker extracts relevant error info
        sendError(adErrorEvent.getError());
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adEvent == null) {
            NRLog.d("NRTrackerIMA: onAdEvent received a null AdEvent."); // Updated log call
            return;
        }

        if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
            NRLog.d("NRTrackerIMA: AdEvent = " + adEvent.getType().name()); // Updated log call
        }

        fillAdAttributes(adEvent.getAd());

        switch (adEvent.getType()) {
            case CONTENT_PAUSE_REQUESTED:
                sendAdBreakStart();
                break;
            case CONTENT_RESUME_REQUESTED:
                sendAdBreakEnd();
                break;
            case STARTED:
                quartile = 0L;
                sendRequest(); // Ad request
                sendStart();   // Ad start
                break;
            case COMPLETED:
            case SKIPPED:
                sendEnd();     // Ad end
                quartile = null; // Reset quartile
                break;
            case TAPPED:
            case CLICKED:
                sendAdClick(); // Ad click
                break;
            case FIRST_QUARTILE:
                quartile = 1L;
                sendAdQuartile();
                break;
            case MIDPOINT:
                quartile = 2L;
                sendAdQuartile();
                break;
            case THIRD_QUARTILE:
                quartile = 3L;
                sendAdQuartile();
                break;
            case PAUSED:
                sendPause();   // Ad pause
                break;
            case RESUMED:
                sendResume();  // Ad resume
                break;
            default:
                NRLog.d("NRTrackerIMA: Unhandled AdEventType: " + adEvent.getType().name()); // Added default case for unhandled types
                break;
        }
    }

    /**
     * Fills ad-specific attributes from the IMA Ad object.
     * @param ad The IMA Ad object.
     */
    private void fillAdAttributes(Ad ad) {
        if (ad == null) {
            NRLog.d("NRTrackerIMA: fillAdAttributes received a null Ad object."); // Updated log call
            return;
        }

        if (ad.getAdPodInfo() != null) {
            switch (ad.getAdPodInfo().getPodIndex()) {
                case 0:
                    adPosition = "pre";
                    break;
                case -1:
                    adPosition = "post";
                    break;
                default:
                    adPosition = "mid";
                    break;
            }
        } else {
            adPosition = null; // Clear if no pod info
        }

        creativeId = ad.getCreativeId();
        title = ad.getTitle();
        bitrate = (long)ad.getVastMediaBitrate();
        renditionHeight = (long)ad.getVastMediaHeight();
        renditionWidth = (long)ad.getVastMediaWidth();
        duration = (long)ad.getDuration();

        NRLog.d("NRTrackerIMA: Filled ad attributes for title: " + title); // Updated log call
    }

    /**
     * Get player name.
     *
     * @return "IMA".
     */
    @Override // Override method from NRVideoTracker
    public String getPlayerName() {
        return "IMA";
    }

    /**
     * Get tracker name.
     *
     * @return "IMATracker".
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerName() {
        return "IMATracker";
    }

    /**
     * Get tracker version.
     *
     * @return The version defined in BuildConfig.
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Get tracker src.
     *
     * @return The source string for this tracker (e.g., SRC constant).
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerSrc() {
        return SRC; // This constant should be defined in NRDef
    }

    /**
     * Get Ad Position.
     *
     * @return "pre", "mid", "post", or null.
     */
    @Override
    public String getAdPosition() {
        return adPosition;
    }

    /**
     * Get Ad Creative ID.
     *
     * @return Creative ID string, or null.
     */
    @Override
    public String getAdCreativeId() {
        return creativeId;
    }

    /**
     * Get Ad Quartile.
     *
     * @return Quartile number (1, 2, 3), or null.
     */
    @Override
    public Long getAdQuartile() {
        return quartile;
    }

    /**
     * Get ad title.
     *
     * @return Ad title string, or null.
     */
    @Override
    public String getTitle() {
        return title;
    }

    /**
     * Get ad bitrate.
     *
     * @return Bitrate in bits per second, or null.
     */
    @Override
    public Long getBitrate() {
        return bitrate;
    }

    /**
     * Get ad rendition height.
     *
     * @return Rendition height in pixels, or null.
     */
    @Override
    public Long getRenditionHeight() {
        return renditionHeight;
    }

    /**
     * Get ad rendition width.
     *
     * @return Rendition width in pixels, or null.
     */
    @Override
    public Long getRenditionWidth() {
        return renditionWidth;
    }

    /**
     * Get ad duration.
     *
     * @return Duration in milliseconds, or null.
     */
    @Override
    public Long getDuration() {
        return duration;
    }
}
