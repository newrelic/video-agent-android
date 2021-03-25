package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test basic tracker workflow.

private const val testName = "Test 1"

class Test1 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as TestContentTracker).setPlayer(null)
        if (!checkPartialResult()) {
            this.callback(testName + " setPlayer", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (!checkPartialResult()) {
            this.callback(testName + " sendRequest(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (!checkPartialResult()) {
            this.callback(testName + " sendRequest(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendStart(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendStart(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        if (!checkPartialResult()) {
            this.callback(testName + " sendPause(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekEnd(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferEnd(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        if (!checkPartialResult()) {
            this.callback(testName + " sendPause(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!checkPartialResult()) {
            this.callback(testName + " sendResume(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!checkPartialResult()) {
            this.callback(testName + " sendResume(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferStart(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferStart(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferEnd(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferEnd(3)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekStart(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekStart(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekEnd(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekEnd(3)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendHeartbeat()
        if (!checkPartialResult()) {
            this.callback(testName + " sendHeartbeat", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendEnd(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendEnd(2)", false)
            return
        }

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        if (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) != null) {
            this.callback(testName + " release", false)
            return
        }

        this.callback(testName, true)
    }

    fun checkPartialResult() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as TestContentTracker).partialResult
    }

    class TestContentTracker : NRVideoTracker() {
        var partialResult = true
        var eventCounter = 0

        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            if (action == NRDef.TRACKER_READY) {
                calcPartialresult(0)
            }
            else if (action == NRDef.PLAYER_READY) {
                calcPartialresult(1)
            }
            else if (action == NRDef.CONTENT_REQUEST) {
                calcPartialresult(2)
            }
            else if (action == NRDef.CONTENT_START) {
                calcPartialresult(3)
            }
            else if (action == NRDef.CONTENT_PAUSE) {
                calcPartialresult(4)
            }
            else if (action == NRDef.CONTENT_RESUME) {
                calcPartialresult(5)
            }
            else if (action == NRDef.CONTENT_BUFFER_START) {
                calcPartialresult(6)
            }
            else if (action == NRDef.CONTENT_BUFFER_END) {
                calcPartialresult(7)
            }
            else if (action == NRDef.CONTENT_SEEK_START) {
                calcPartialresult(8)
            }
            else if (action == NRDef.CONTENT_SEEK_END) {
                calcPartialresult(9)
            }
            else if (action == NRDef.CONTENT_HEARTBEAT) {
                calcPartialresult(10)
            }
            else if (action == NRDef.CONTENT_END) {
                calcPartialresult(11)
            }

            return false
        }

        fun calcPartialresult(index: Int) {
            if (eventCounter != index) {
                partialResult = false
            }
            eventCounter = eventCounter + 1
        }
    }
}