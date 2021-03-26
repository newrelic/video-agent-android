package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test Ad specific stuff.

private val testName = "Test 7"
private var numberOfAds = -1
private var buffType = ""
private var ADBREAK_TIME : Long = 0L

class Test7 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker(), TestAdTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).setPlayer(null)

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendAdBreakStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (buffType != "ad") {
            this.callback(testName + " sendBufferStart", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendRequest()
        Thread.sleep(500)
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendEnd()

        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendRequest()
        Thread.sleep(500)
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendEnd()

        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendAdBreakEnd()
        if (!checkAdPartialResult()) {
            this.callback(testName + " sendAdBreakEnd", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (buffType != "ad") {
            this.callback(testName + " sendBufferEnd", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!(numberOfAds == 2)) {
            this.callback(testName + " content sendStart", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (buffType != "initial") {
            this.callback(testName + " sendBufferStart(2)", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (buffType != "initial") {
            this.callback(testName + " sendBufferEnd(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendAdBreakStart()
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendRequest()
        Thread.sleep(500)
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendEnd()
        if (numberOfAds != 3) {
            this.callback(testName + " content ad sendEnd", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as NRVideoTracker).sendAdBreakEnd()

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        this.callback(testName, true)
    }

    fun checkAdPartialResult() : Boolean {
        return (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) as TestAdTracker).partialResult
    }

    class TestContentTracker : NRVideoTracker() {
        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            if (attributes!!["numberOfAds"] != null) {
                val n = attributes!!["numberOfAds"] as Int
                numberOfAds = n
            }

            if (action == NRDef.CONTENT_BUFFER_START || action == NRDef.CONTENT_BUFFER_END) {
                if (attributes!!["bufferType"] != null) {
                    val bt = attributes!!["bufferType"] as String
                    buffType = bt
                }
            }

            return false
        }
    }

    class TestAdTracker : NRVideoTracker() {
        var partialResult = true
        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send Ad event = " + action + " , attr = " + attributes)

            if (attributes!!["numberOfAds"] != null) {
                val n = attributes!!["numberOfAds"] as Int
                numberOfAds = n
            }

            if (action == NRDef.AD_END) {
                if (attributes!!["timeSinceAdRequested"] != null) {
                    val ts = attributes!!["timeSinceAdRequested"] as Long
                    ADBREAK_TIME = ADBREAK_TIME + ts
                }
            }
            else if (action == NRDef.AD_BREAK_END) {
                if (attributes!!["timeSinceAdBreakBegin"] != null) {
                    val ts = attributes!!["timeSinceAdBreakBegin"] as Long
                    if (ts < ADBREAK_TIME || ts > ADBREAK_TIME + 100) {
                        partialResult = false
                    }
                }
            }

            return false
        }
    }
}