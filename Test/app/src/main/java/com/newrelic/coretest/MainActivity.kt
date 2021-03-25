package com.newrelic.coretest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.newrelic.videoagent.core.NewRelicVideoAgent
import com.newrelic.videoagent.core.tracker.NRVideoTracker

class MainActivity : AppCompatActivity() {
    val trackerId = NewRelicVideoAgent.getInstance().start(NRVideoTracker())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}