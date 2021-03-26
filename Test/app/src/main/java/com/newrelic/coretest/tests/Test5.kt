package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test tracker states.

private const val testName = "Test 5"

class Test5 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).setPlayer(null)
        if (!checkPlayerReady()) {
            this.callback(testName + " isPlayerReady", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (!checkRequested()) {
            this.callback(testName + " isRequested", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!(checkStarted() && checkPlaying())) {
            this.callback(testName + " isStarted", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        if (!checkPaused()) {
            this.callback(testName + " isPaused", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!checkPlaying()) {
            this.callback(testName + " isPlaying", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkBuffering()) {
            this.callback(testName + " isBuffering", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (checkBuffering()) {
            this.callback(testName + " not isBuffering", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        if (!checkSeeking()) {
            this.callback(testName + " isSeeking", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (checkSeeking()) {
            this.callback(testName + " not isSeeking", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        if (!checkSeeking()) {
            this.callback(testName + " isSeeking", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!(checkBuffering() && checkPaused() && !checkSeeking())) {
            this.callback(testName + " isBuffering & isPaused & not iSeeking", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!(checkPlaying() && !checkBuffering())) {
            this.callback(testName + " isPlaying & not isBuffering", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!(!checkPlaying() && !checkBuffering() && !checkPaused() && !checkSeeking() && !checkStarted())) {
            this.callback(testName + " END state", false)
            return
        }

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        this.callback(testName, true)
    }

    fun checkPlayerReady() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isPlayerReady
    }

    fun checkRequested() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isRequested
    }

    fun checkStarted() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isStarted
    }

    fun checkPaused() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isPaused
    }

    fun checkBuffering() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isBuffering
    }

    fun checkSeeking() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isSeeking
    }

    fun checkPlaying() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).state.isPlaying
    }

    class TestContentTracker : NRVideoTracker() {
        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            return false
        }
    }
}