package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

//import com.newrelic.agent.android.NewRelic;
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
                attr.put("actionName", "AGENT_TEST");
                attr.put("kind", "counter");
                attr.put("counter", counter++);
                if (NewRelic.recordCustomEvent("MobileVideo", attr)) {
                    Log.v("", "-------> AGENT_TEST sent");
                }
                else {
                    Log.v("","-------> AGENT_TEST not sent");
                }

                Map<String, Object> emptyAttr = new HashMap<>();
                if (NewRelic.recordCustomEvent("SimpleEvent", emptyAttr)) {
                    Log.v("", "-------> Empty event sent");
                }
                else {
                    Log.v("", "-------> Empty event not sent");
                }
            }
        });

        //WARNING: DEFINE THE APP TOKEN HERE
        NewRelic.withApplicationToken("APP TOKEN").start(this.getApplication());

        NRLog.enable();

        Map<String, Object> attr = new HashMap<>();
        attr.put("actionName", "AGENT_TEST");
        attr.put("intVal", 1001);
        attr.put("floatVal", 1.23);
        attr.put("strVal", "this is a string");
        attr.put("kind", "app start");
        if (NewRelic.recordCustomEvent("MobileVideo", attr)) {
            Log.v("", "-------> AGENT_TEST_INIT sent");
        }
        else {
            Log.v("","-------> AGENT_TEST_INIT not sent");
        }
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