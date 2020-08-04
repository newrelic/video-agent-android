package ca.bellmedia.lib.vidi.analytics.qos.trackers

import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.newrelic.videoagent.NRLog
import com.newrelic.videoagent.basetrackers.AdsTracker
import com.newrelic.videoagent.basetrackers.ContentsTracker
import com.newrelic.videoagent.jni.swig.CoreTrackerState
import java.util.concurrent.TimeUnit

/**
 *  Class implementing NewRelic AdsTracker for the Google IMA SDK
 */
class GoogleImaNewRelicAdsTracker(contentsTracker: ContentsTracker) : AdsTracker(contentsTracker), AdEvent.AdEventListener, AdErrorEvent.AdErrorListener {

    var adsManager: AdsManager? = null
        set(value) {
            NRLog.d("FIELD = " + field)
            field?.removeAdEventListener(this)
            field?.removeAdErrorListener(this)
            field = value
            field?.addAdEventListener(this)
            field?.addAdErrorListener(this)
        }

    /**
     *  Removes current listeners to the AdsManager instance
     *  Send ad end events if needed when released
     */
    fun release() {
        adsManager?.removeAdEventListener(this)
        adsManager?.removeAdErrorListener(this)
        if (state() != CoreTrackerState.CoreTrackerStateStopped) {
            sendEnd()
            sendAdBreakEnd()
        }
    }

    private var currentCreativeAdId: String? = null
    private var currentAdId: String? = null
    private var currentAdDuration: Double? = null
    private var currentAdPosition: String? = null
    private var currentNumberOfAds: Int? = null

    override fun getPlayerName() = "ExoPlayer2"

    override fun getPlayerVersion() = "2.x"

    override fun getTrackerName() = "Google IMA Ads Tracker"

    override fun getTrackerVersion() = "0.1.0"

    fun getAdPartner() = "ima"

    fun getAdCreativeId() = currentCreativeAdId

    fun getDuration() = currentAdDuration

    fun getAdPosition() = currentAdPosition

    fun getAdId() = currentAdId

    override fun getNumberOfAds() = currentNumberOfAds ?: 0

    override fun onAdEvent(adEvent: AdEvent?) {
        adEvent?.let {
            updateAdAttributes(it)
            handleAdEvents(it)
            if (adEvent.type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED
                || adEvent.type == AdEvent.AdEventType.AD_BREAK_ENDED
                || adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED
                || adEvent.type == AdEvent.AdEventType.SKIPPED) {
                clearAdAttributes()
            }
        }
    }

    private fun handleAdEvents(adEvent: AdEvent) {
        when (adEvent.type) {
            AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED -> sendAdBreakStart()
            AdEvent.AdEventType.STARTED -> {
                sendRequest()
                sendStart()
            }
            AdEvent.AdEventType.COMPLETED -> sendEnd()
            AdEvent.AdEventType.CONTENT_RESUME_REQUESTED -> sendAdBreakEnd()
            else -> {
            }
        }
    }

    override fun onAdError(adErrorEvent: AdErrorEvent?) {
        val error = adErrorEvent?.error
        val errorMsg = "Type: ${error?.errorType} Code: ${error?.errorCode} Message: ${error?.message}"
        sendError(errorMsg)
        clearAdAttributes()
    }

    private fun updateAdAttributes(adEvent: AdEvent) {
        adEvent.ad?.apply {
            currentCreativeAdId = creativeId
            currentAdId = adId
            currentAdDuration = TimeUnit.SECONDS.toMillis(duration.toLong()).toDouble()
            currentAdPosition = when (adPodInfo.podIndex) {
                0 -> "pre"
                -1 -> "post"
                else -> "mid"
            }
            currentNumberOfAds = adPodInfo?.totalAds
        }
    }

    private fun clearAdAttributes() {
        currentCreativeAdId = null
        currentAdDuration = null
        currentAdPosition = null
        currentNumberOfAds = null
    }

}