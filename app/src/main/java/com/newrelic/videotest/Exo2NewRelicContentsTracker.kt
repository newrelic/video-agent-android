package ca.bellmedia.lib.vidi.analytics.qos.trackers

import com.google.android.exoplayer2.SimpleExoPlayer
import com.newrelic.videoagent.jni.swig.AttrList
import com.newrelic.videoagent.jni.swig.CoreTrackerState
import com.newrelic.videoagent.trackers.ExoPlayer2ContentsTracker

/**
 *  Class adding custom behavior to ExoPlayer2ContentsTracker
 *  - onPreSendEvent is trigerred before every event is sent.
 *  - an end event is sent if the tracker is released.
 */
class Exo2NewRelicContentsTracker(player: SimpleExoPlayer) : ExoPlayer2ContentsTracker(player) {

    var listener: Listener? = null

    override fun sendHeartbeat() {
        listener?.onPreSendEvent()
        super.sendHeartbeat()
    }

    override fun sendRenditionChange() {
        listener?.onPreSendEvent()
        super.sendRenditionChange()
    }

    override fun sendBufferEnd() {
        listener?.onPreSendEvent()
        super.sendBufferEnd()
    }

    override fun sendBufferStart() {
        listener?.onPreSendEvent()
        super.sendBufferStart()
    }

    override fun sendPlayerReady() {
        listener?.onPreSendEvent()
        super.sendPlayerReady()
    }

    override fun sendSeekStart() {
        listener?.onPreSendEvent()
        super.sendSeekStart()
    }

    override fun sendSeekEnd() {
        listener?.onPreSendEvent()
        super.sendSeekEnd()
    }

    override fun sendStart() {
        listener?.onPreSendEvent()
        super.sendStart()
    }

    override fun sendResume() {
        listener?.onPreSendEvent()
        super.sendResume()
    }

    override fun sendError(message: String?) {
        listener?.onPreSendEvent()
        super.sendError(message)
    }

    override fun sendEnd() {
        listener?.onPreSendEvent()
        super.sendEnd()
    }

    override fun sendDownload() {
        listener?.onPreSendEvent()
        super.sendDownload()
    }

    override fun sendPause() {
        listener?.onPreSendEvent()
        super.sendPause()
    }

    override fun sendRequest() {
        listener?.onPreSendEvent()
        super.sendRequest()
    }

    override fun sendDroppedFrame(count: Int, elapsed: Int) {
        listener?.onPreSendEvent()
        super.sendDroppedFrame(count, elapsed)
    }

    override fun sendCustomAction(name: String?) {
        listener?.onPreSendEvent()
        super.sendCustomAction(name)
    }

    override fun sendCustomAction(name: String?, attr: AttrList?) {
        listener?.onPreSendEvent()
        super.sendCustomAction(name, attr)
    }

    /**
     *  Send end event if needed on release
     */
    fun release() {
        if (state() != CoreTrackerState.CoreTrackerStateStopped) {
            sendEnd()
        }
    }

    /**
     *  Listener with onPreSendEvent triggered before every event sent
     */
    interface Listener {
        fun onPreSendEvent()
    }
}