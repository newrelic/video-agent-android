package com.newrelic.coretest.tests

import android.util.Log
import com.newrelic.videoagent.core.NRDef
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

// Test counters and IDs.

private const val testName = "Test 6"
private var numberOfVideos = -1
private var numberOfErrors = -1
private var viewIds : MutableMap<String, Boolean> = mutableMapOf()

class Test6 : TestInterface {

    lateinit var callback : (String, Boolean) -> Unit
    val trackerId = NewRelicVideoAgent.getInstance().start(TestContentTracker())

    override fun doTest(callback: (String, Boolean) -> Unit) {
        this.callback = callback

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).setPlayer(null)

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (viewIds.size != 1) {
            this.callback(testName + " sendRequest(1)", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!(numberOfVideos == 1 && numberOfErrors == 0)) {
            this.callback(testName + " sendEnd(1)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (viewIds.size != 2) {
            this.callback(testName + " sendRequest(2)", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendError()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!(numberOfVideos == 2 && numberOfErrors == 1)) {
            this.callback(testName + " sendEnd(2)", false)
            return
        }

        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendRequest()
        if (viewIds.size != 3) {
            this.callback(testName + " sendRequest(3)", false)
            return
        }
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendError()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendStart()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendError()
        (NewRelicVideoAgent.getInstance().getContentTracker(trackerId) as NRVideoTracker).sendEnd()
        if (!(numberOfVideos == 3 && numberOfErrors == 2)) {
            this.callback(testName + " sendEnd(3)", false)
            return
        }

        NewRelicVideoAgent.getInstance().releaseTracker(trackerId)

        this.callback(testName, true)
    }

    class TestContentTracker : NRVideoTracker() {
        override fun preSend(action: String?, attributes: MutableMap<String, Any>?): Boolean {
            Log.v("CoreTest", "Send event = " + action + " , attr = " + attributes)

            if (action == NRDef.CONTENT_REQUEST) {
                if (attributes!!["viewId"] != null) {
                    val vid = attributes!!["viewId"] as String
                    viewIds[vid] = true
                }
            }
            else if (action == NRDef.CONTENT_END) {
                if (attributes!!["numberOfVideos"] != null) {
                    val n = attributes!!["numberOfVideos"] as Int
                    numberOfVideos = n
                }
                if (attributes["numberOfErrors"] != null) {
                    val n = attributes!!["numberOfErrors"] as Int
                    numberOfErrors = n
                }
            }

            return false
        }
    }
}