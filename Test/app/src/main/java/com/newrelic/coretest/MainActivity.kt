package com.newrelic.coretest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.newrelic.coretest.tests.Test1

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val testArray = arrayOf(Test1())

        testArray.forEach {
            it.doTest(::testResult)
        }
    }

    fun testResult(name: String, result: Boolean) {
        Log.v("CoreTest", "Test Result called = " + name + " " + result)
    }
}