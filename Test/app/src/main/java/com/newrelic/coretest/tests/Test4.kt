package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test playtimes.

private const val testName = "Test 4"

class Test4 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (!checkPartialResult()) {
            this.callback(testName + " sendRequest", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendStart", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        if (!checkPartialResult()) {
            this.callback(testName + " sendPause", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!checkPartialResult()) {
            this.callback(testName + " sendResume", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendHeartbeat()
        if (!checkPartialResult()) {
            this.callback(testName + " sendHeartbeat", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferStart", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendBufferEnd", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekStart", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendSeekEnd", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEvent("TEST_ACTION")
        if (!checkPartialResult()) {
            this.callback(testName + " send TEST_ACTION", false)
            return
        }
        Thread.sleep(1000)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " sendEnd", false)
            return
        }

        this.callback(testName, true)
    }

    fun checkPartialResult() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as TestContentTracker).partialResult
    }

    class TestContentTracker : NRVideoTracker() {
        var partialResult = true
        var totalPlaytime = 0L
        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)
            
            if (action == NRDef.CONTENT_REQUEST) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 0)
            }
            else if (action == NRDef.CONTENT_START) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 0)
            }
            else if (action == NRDef.CONTENT_PAUSE) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }
            else if (action == NRDef.CONTENT_RESUME) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 0)
            }
            else if (action == NRDef.CONTENT_HEARTBEAT) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }
            else if (action == NRDef.CONTENT_BUFFER_START) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }
            else if (action == NRDef.CONTENT_BUFFER_END) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 0)
            }
            else if (action == NRDef.CONTENT_SEEK_START) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }
            else if (action == NRDef.CONTENT_SEEK_END) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 0)
            }
            else if (action == "TEST_ACTION") {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }
            else if (action == NRDef.CONTENT_END) {
                partialResult = checkPlaytimes(attributes!!["totalPlaytime"] as Long, attributes!!["playtimeSinceLastEvent"] as Long, 1000)
            }

            return false
        }

        fun checkPlaytimes(total: Long, sinceLast: Long, timeRef: Long) : Boolean {
            totalPlaytime = totalPlaytime + sinceLast

            if (total != totalPlaytime) {
                return false
            }
            if (sinceLast < timeRef || sinceLast > timeRef + 200) {
                return false
            }

            return true
        }
    }
}