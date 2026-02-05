package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.config.NRVideoConfig;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Switch adsSwitch;
    Switch qoeSwitch;
    int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NRVideoConfiguration config = new NRVideoConfiguration.Builder("AAdc5e75073d8720260b9b0139b9a6fc686b954936-NRMA")
                .autoDetectPlatform(getApplicationContext())
                .withHarvestCycle(60)
                .enableLogging()
                .build();
        NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
        // Initialize QOE configuration with value from local.properties
        NRVideoConfig.getInstance().initializeDefaults(BuildConfig.QOE_AGGREGATE_DEFAULT);
        setContentView(R.layout.activity_main);

        adsSwitch = findViewById(R.id.ads_switch);
        qoeSwitch = findViewById(R.id.qoe_switch);

        // Initialize QOE switch with current configuration state
        qoeSwitch.setChecked(NRVideoConfig.getInstance().isQoeAggregateEnabled());

        // Set up QOE switch listener
        qoeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Toggle QOE aggregate functionality at runtime
            NRVideoConfig.getInstance().setQoeAggregateEnabled(isChecked);
            android.util.Log.d("QOE_TOGGLE", "QOE Aggregate " + (isChecked ? "ENABLED" : "DISABLED"));

            // Show user feedback
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
