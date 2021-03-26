package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test start time, buffering time, pause time, seeking time and custom time since.

private const val testName = "Test 2"
private const val TTFF : Long = 1100
private const val BUFFER_TIME : Long = 800
private const val SEEK_TIME : Long = 1000
private const val PAUSE_TIME : Long = 1200
private var TESTACTION_TIME : Long = 0

class Test2 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())
    
    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).addTimeSinceEntry("TEST_ACTION", "timeSinceTestAction", "^[A-Z_]+$")

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).setPlayer(null)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEvent("TEST_ACTION")
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()

        Thread.sleep(TTFF)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        if (!checkPartialResult()) {
            this.callback(testName + " TTFF", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferStart()
        Thread.sleep(BUFFER_TIME)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendBufferEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " BUFFER_TIME", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekStart()
        Thread.sleep(SEEK_TIME)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendSeekEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " SEEK_TIME", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendPause()
        Thread.sleep(PAUSE_TIME)
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendResume()
        if (!checkPartialResult()) {
            this.callback(testName + " PAUSE_TIME", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!checkPartialResult()) {
            this.callback(testName + " REQUESTED_TIME", false)
            return
        }

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        this.callback(testName, true)
    }

    fun checkPartialResult() : Boolean {
        return (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as TestContentTracker).partialResult
    }
    
    class TestContentTracker : NRVideoTracker() {
        var partialResult = true

        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            if (action == NRDef.CONTENT_START) {
                checkTimeSinceAttribute(attributes!!, "timeSinceRequested", TTFF)
            }
            else if (action == NRDef.CONTENT_BUFFER_END) {
                checkTimeSinceAttribute(attributes!!, "timeSinceBufferBegin", BUFFER_TIME)
            }
            else if (action == NRDef.CONTENT_SEEK_END) {
                checkTimeSinceAttribute(attributes!!, "timeSinceSeekBegin", SEEK_TIME)
            }
            else if (action == NRDef.CONTENT_RESUME) {
                checkTimeSinceAttribute(attributes!!, "timeSincePaused", PAUSE_TIME)
            }
            else if (action == NRDef.CONTENT_END) {
                checkTimeSinceAttribute(attributes!!, "timeSinceTestAction", TESTACTION_TIME)
            }

            return false
        }

        fun checkTimeSinceAttribute(attr: MutableMap<String, Any>, name: String, target: Long) {
            if (attr.containsKey(name)) {
                val ts = attr[name] as Long
                Log.v("CoreTest", "time attribute " + name + " = " + ts)
                TESTACTION_TIME = TESTACTION_TIME + ts
                if (!checkTimeSince(ts, target)) {
                    partialResult = false
                }
            }
            else {
                Log.v("CoreTest", "time attribute false")
                partialResult = false
            }
        }

        fun checkTimeSince(value: Long, target: Long) : Boolean {
            return value >= target && value < target + 100   //100ms margin
        }
    }
}