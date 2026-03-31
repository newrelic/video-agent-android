package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoConfiguration;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Switch adsSwitch;
    Switch qoeSwitch;
    int counter = 0;
    NRVideoConfiguration config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(BuildConfig.NR_APPLICATION_TOKEN)
                .autoDetectPlatform(getApplicationContext())
                .withHarvestCycle(60)
                .enableLogging()
                .enableQoeAggregate(BuildConfig.QOE_AGGREGATE_DEFAULT)
                .build();
        NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
        setContentView(R.layout.activity_main);

        adsSwitch = findViewById(R.id.ads_switch);
        qoeSwitch = findViewById(R.id.qoe_switch);

        // Initialize QOE switch with current configuration state
        qoeSwitch.setChecked(config.isQoeAggregateEnabled());

        // Set up QOE switch listener with optimized UI operations
        qoeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Perform config update on background thread to avoid UI blocking
                    // Toggle QOE aggregate functionality at runtime
                    config.setQoeAggregateEnabled(isChecked);

                    // Show user feedback on UI thread
                        String message = "QOE Aggregate " + (isChecked ? "Enabled" : "Disabled");
                        android.widget.Toast.makeText(MainActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.video0).setOnClickListener(this);
        findViewById(R.id.video1).setOnClickListener(this);
        findViewById(R.id.video2).setOnClickListener(this);
        findViewById(R.id.video3).setOnClickListener(this);
        findViewById(R.id.video4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Map<String, Object> attr = new HashMap<>();
                attr.put("kind", "counter");
                attr.put("actionName", "CLICK");
                attr.put("counter", counter++);
                NRVideo.recordCustomEvent(attr);

                Map<String, Object> emptyAttr = new HashMap<>();
                NRVideo.recordCustomEvent(emptyAttr);
            }
        });

        Map<String, Object> attr = new HashMap<>();
        attr.put("actionName", "AGENT_TEST");
        attr.put("intVal", 1001);
        attr.put("floatVal", 1.23);
        attr.put("strVal", "this is a string");
        attr.put("kind", "app start successfully");
        NRVideo.recordCustomEvent(attr);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, getVideoActivity());
        Object tag = v.getTag();
        String videoTag = tag != null ? tag.toString() : "default";
        intent.putExtra("video", videoTag);
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
