package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test buffer type.

private const val testName = "Test 3"
private var startTimestamp : Long = 0

class Test3 : TestInterface {
    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).setPlayer(null)

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 0 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 0 end", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        startTimestamp = System.currentTimeMillis()

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 1 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 1 end", false)
            return
        }

        Thread.sleep(1500)

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 2 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 2 end", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 3 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 3 end", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 4 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 4 end", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 5 start", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " buffer 5 end", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        this.callback(testName, true)
    }

    fun checkPartialResult() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as TestContentTracker).partialResult
    }

    class TestContentTracker : NRVideoTracker() {
        var partialResult = true
        var bufferingCounter = 0

        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            if (action == NRDef.CONTENT_BUFFER_START) {
                partialResult = checkBufferType(attributes!!["bufferType"] as String)
            }
            else if (action == NRDef.CONTENT_BUFFER_END) {
                partialResult = checkBufferType(attributes!!["bufferType"] as String)
                bufferingCounter++
            }

            return false
        }

        fun checkBufferType(type: String) : Boolean {
            return when (bufferingCounter) {
                0 -> type == "initial"
                1 -> type == "initial"
                2 -> type == "connection"
                3 -> type == "pause"
                4 -> type == "seek"
                5 -> type == "seek"
                else -> false
            }
        }

        override fun getPlayhead() : Long {
            return if (startTimestamp > 0) System.currentTimeMillis() - startTimestamp else 0
        }
    }
}