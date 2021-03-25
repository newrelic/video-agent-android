package com.newrelic.coretest

import android.app.Application
import com.newrelic.videoagent.core.utils.NRLog

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()

        NRLog.disable()
    }
}