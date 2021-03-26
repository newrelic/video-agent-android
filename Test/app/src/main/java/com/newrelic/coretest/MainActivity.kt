package com.newrelic.coretest

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.newrelic.coretest.tests.*

class MainActivity : AppCompatActivity() {
    lateinit var textView : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView = findViewById<TextView>(R.id.text_view)
        val testArray = arrayOf(Test1(), Test2(), Test3(), Test4(), Test5())

        appendLine("Running " + testArray.size + " tests...\n\n\n")

        Thread(Runnable {
                    kotlin.run {
                        testArray.forEach {
                            it.doTest(::testResult)
                        }
                    }
                }).start()
    }

    fun testResult(name: String, result: Boolean) {
        runOnUiThread {
            Log.v("CoreTest", "Test Result called = " + name + " " + result)
            appendLine(name + "\t\t" + (if (result) "✅" else "❌") + "\n\n")
        }
    }

    fun appendLine(text: String) {
        textView.text = textView.text.toString() + text
    }
}