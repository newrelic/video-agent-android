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
    int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(BuildConfig.NR_APPLICATION_TOKEN)
                .autoDetectPlatform(getApplicationContext())
                .withHarvestCycle(60)
                .enableLogging()
                .build();
        NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
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
        attr.put("kind", "app start");
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
