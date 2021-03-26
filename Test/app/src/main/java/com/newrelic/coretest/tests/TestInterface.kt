package com.newrelic.coretest.tests

import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

interface TestInterface {
    fun doTest(callback: (String, Boolean) -> Unit)
}