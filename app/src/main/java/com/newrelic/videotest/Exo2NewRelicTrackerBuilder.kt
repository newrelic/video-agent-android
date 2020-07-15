package ca.bellmedia.lib.vidi.analytics.qos.trackers

import android.net.Uri
import com.google.android.exoplayer2.SimpleExoPlayer
import com.newrelic.videoagent.NRLog
import com.newrelic.videoagent.TrackerBuilder
import com.newrelic.videoagent.basetrackers.AdsTracker
import com.newrelic.videoagent.basetrackers.ContentsTracker

/**
 *  Class allowing to initialize NewRelicVideo with both custom implementation of contents and ads trackers
 */
class Exo2NewRelicTrackerBuilder : TrackerBuilder() {
    private var contentsTracker: ContentsTracker? = null
    private var adsTracker: AdsTracker? = null

    override fun contents(): ContentsTracker? {
        return contentsTracker
    }

    override fun ads(): AdsTracker? {
        return adsTracker
    }

    override fun startWithPlayer(trackerBuilderData: Any, videoUri: Uri?) {
        NRLog.d("Starting Video Agent with player and one video")
        this.initPlayer(trackerBuilderData)
    }

    override fun startWithPlayer(trackerBuilderData: Any, playlist: List<Uri?>?) {
        NRLog.d("Starting Video Agent with player and a playlist")
        this.initPlayer(trackerBuilderData)
    }

    override fun startWithPlayer(trackerBuilderData: Any) {
        NRLog.d("Starting Video Agent with player")
        this.initPlayer(trackerBuilderData)
    }

    private fun initPlayer(trackerBuilderData: Any) {
        if (trackerBuilderData is TrackerBuilderData && trackerBuilderData.player is SimpleExoPlayer) {
            contentsTracker = Exo2NewRelicContentsTracker(trackerBuilderData.player).also {
                if (trackerBuilderData.adsTrackingEnabled) {
                    adsTracker = GoogleImaNewRelicAdsTracker(it)
                }
            }
        } else {
            throw Error("Player is not a instance of SimpleExoPlayer")
        }
    }
}