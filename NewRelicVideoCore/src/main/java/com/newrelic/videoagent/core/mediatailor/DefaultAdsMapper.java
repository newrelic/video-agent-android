package com.newrelic.videoagent.core.mediatailor;

import android.util.Log;

import com.newrelic.videoagent.core.mediatailor.model.Ad;
import com.newrelic.videoagent.core.mediatailor.model.Avail;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorLinearAd;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorTrackingEvent;
import com.newrelic.videoagent.core.mediatailor.model.TrackingEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper to transform MediaTailor API response DTOs to domain models.
 * Converts Avails to MediaTailorAdBreak objects with nested ads and tracking events.
 */
public class DefaultAdsMapper {
    private static final String TAG = "MediaTailor.Mapper";

    /**
     * Map API avails to domain ad breaks.
     *
     * @param avails List of avails from MediaTailor API
     * @return List of mapped ad breaks
     */
    public List<MediaTailorAdBreak> mapAdBreaks(List<Avail> avails) {
        Log.d(TAG, "Mapping " + (avails != null ? avails.size() : 0) + " avails to ad breaks");

        if (avails == null || avails.isEmpty()) {
            Log.d(TAG, "No avails to map, returning empty list");
            return new ArrayList<>();
        }

        List<MediaTailorAdBreak> adBreaks = new ArrayList<>();

        for (Avail avail : avails) {
            try {
                MediaTailorAdBreak adBreak = mapAvailToAdBreak(avail);
                adBreaks.add(adBreak);
                Log.d(TAG, "Mapped ad break: " + adBreak);
            } catch (Exception e) {
                Log.e(TAG, "Error mapping avail: " + avail.getAvailId(), e);
            }
        }

        Log.d(TAG, "Successfully mapped " + adBreaks.size() + " ad breaks");
        return adBreaks;
    }

    /**
     * Map a single Avail to MediaTailorAdBreak.
     *
     * @param avail The avail from API
     * @return Mapped ad break
     */
    private MediaTailorAdBreak mapAvailToAdBreak(Avail avail) {
        List<MediaTailorLinearAd> ads = new ArrayList<>();

        if (avail.getAds() != null) {
            for (Ad ad : avail.getAds()) {
                try {
                    MediaTailorLinearAd linearAd = mapAdToLinearAd(ad);
                    ads.add(linearAd);
                } catch (Exception e) {
                    Log.e(TAG, "Error mapping ad: " + ad.getAdId(), e);
                }
            }
        }

        return new MediaTailorAdBreak(
                avail.getAvailId(),
                ads,
                avail.getStartTimeInSeconds(),
                avail.getDurationInSeconds(),
                avail.getDuration(),
                avail.getAdMarkerDuration()
        );
    }

    /**
     * Map a single Ad to MediaTailorLinearAd.
     *
     * @param ad The ad from API
     * @return Mapped linear ad
     */
    private MediaTailorLinearAd mapAdToLinearAd(Ad ad) {
        List<MediaTailorTrackingEvent> trackingEvents = new ArrayList<>();

        if (ad.getTrackingEvents() != null) {
            for (TrackingEvent event : ad.getTrackingEvents()) {
                try {
                    MediaTailorTrackingEvent trackingEvent = mapTrackingEvent(event);
                    trackingEvents.add(trackingEvent);
                } catch (Exception e) {
                    Log.e(TAG, "Error mapping tracking event: " + event.getEventId(), e);
                }
            }
        }

        return new MediaTailorLinearAd(
                ad.getAdId(),
                ad.getStartTimeInSeconds(),
                ad.getDurationInSeconds(),
                ad.getDuration(),
                trackingEvents
        );
    }

    /**
     * Map a single TrackingEvent to MediaTailorTrackingEvent.
     *
     * @param event The tracking event from API
     * @return Mapped tracking event
     */
    private MediaTailorTrackingEvent mapTrackingEvent(TrackingEvent event) {
        return new MediaTailorTrackingEvent(
                event.getEventId(),
                event.getStartTimeInSeconds(),
                event.getDurationInSeconds(),
                event.getEventType(),
                event.getBeaconUrls()
        );
    }
}
