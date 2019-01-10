package com.newrelic.videotest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.NewRelicVideoAgent;

public class Exo1Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo1);

        NRLog.enable();
        setupPlayer();
    }

    void setupPlayer() {

    }
}
