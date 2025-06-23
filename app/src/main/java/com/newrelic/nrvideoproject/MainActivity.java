package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.telemetry.analytics.VideoAnalyticsController;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Switch adsSwitch;
    int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adsSwitch = findViewById(R.id.ads_switch);

        findViewById(R.id.video0).setOnClickListener(this);
        findViewById(R.id.video1).setOnClickListener(this);
        findViewById(R.id.video2).setOnClickListener(this);
        findViewById(R.id.video3).setOnClickListener(this);
        findViewById(R.id.video4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Map<String, Object> attr = new HashMap<>();
                attr.put("actionName", "PLAY_START_108"); // Changed to match curl example "actionName"
                attr.put("instrumentation.provider", "MainActivity"); // Use a relevant provider
                attr.put("instrumentation.name", "TestApp");
                attr.put("instrumentation.version", "1.0.0"); // Example version for this app
                attr.put("kind", "counter");
                attr.put("counter", counter++);

                // FIX: Delegate to VideoAnalyticsController for custom events
                if (VideoAnalyticsController.getInstance().recordCustomEvent("VideoAction", attr)) { // Changed eventType to "VideoAction" as in curl
                    NRLog.d("-------> VideoAction AGENT_TEST sent from MainActivity");
                }
                else {
                    NRLog.d("-------> VideoAction AGENT_TEST not sent from MainActivity");
                }

                Map<String, Object> emptyAttr = new HashMap<>();
                // FIX: Delegate to VideoAnalyticsController for custom events
                if (VideoAnalyticsController.getInstance().recordCustomEvent("SimpleEvent", emptyAttr)) {
                    NRLog.d("-------> Empty event sent from MainActivity");
                }
                else {
                    NRLog.d("-------> Empty event not sent from MainActivity");
                }
            }
        });

        // MODIFIED: Initialize the standalone NewRelicVideoAgent
        // The application token will now be handled internally by VideoAgentConfiguration,
        // but you still pass it here as the entry point.
        // It should match the token hardcoded in VideoAgentConfiguration.java for this POC.
        NewRelicVideoAgent.initialize("AAb789c05df687cecaa33bc6b159e2a76a7cac9f65-NRMA", this.getApplicationContext());

        // Enable NRLog to see debug messages from the Video Agent modules
        NRLog.enable();

        Map<String, Object> attr = new HashMap<>();
        attr.put("actionName", "APP_START_INIT"); // Changed action name for clarity
        attr.put("intVal", 1001);
        attr.put("floatVal", 1.23);
        attr.put("strVal", "this is a string");
        attr.put("kind", "app start");
        attr.put("instrumentation.provider", "MainActivity"); // Add instrumentation attributes
        attr.put("instrumentation.name", "TestApp");
        attr.put("instrumentation.version", "1.0.0");


        // FIX: Delegate to VideoAnalyticsController for custom events
        if (VideoAnalyticsController.getInstance().recordCustomEvent("AppLifecycle", attr)) { // Changed eventType
            NRLog.d("-------> AppLifecycle AGENT_TEST_INIT sent from MainActivity");
        }
        else {
            NRLog.d("-------> AppLifecycle AGENT_TEST_INIT not sent from MainActivity");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // OPTIONAL: If you want to explicitly shut down the video agent when MainActivity is destroyed
        // In a real app, you might shut it down on Application.onTerminate() or keep it running.
        NewRelicVideoAgent.shutdown();
        NRLog.d("NewRelicVideoAgent shutdown triggered from MainActivity onDestroy.");
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, getVideoActivity());
        intent.putExtra("video", (String) v.getTag());
        startActivity(intent);
    }

    Class getVideoActivity() {
        if (adsSwitch.isChecked()) {
            return VideoPlayerAds.class;
        }
        else {
            return VideoPlayer.class;
        }
    }
}