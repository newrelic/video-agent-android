package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.newrelic.agent.android.NewRelic;
import com.newrelic.videoagent.core.utils.NRLog;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.video0).setOnClickListener(this);
        findViewById(R.id.video1).setOnClickListener(this);
        findViewById(R.id.video2).setOnClickListener(this);
        findViewById(R.id.video3).setOnClickListener(this);

        //WARNING: DEFINE THE APP TOKEN HERE
        NewRelic.withApplicationToken("APP TOKEN").start(this.getApplication());

        NRLog.enable();
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, VideoPlayer.class);
        intent.putExtra("video", (String)v.getTag());
        startActivity(intent);
    }
}