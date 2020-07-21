package com.newrelic.videotest;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.newrelic.videoagent.NRLog;

public class VideoList extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);

        findViewById(R.id.video0).setOnClickListener(this);
        findViewById(R.id.video1).setOnClickListener(this);
        findViewById(R.id.video2).setOnClickListener(this);
        findViewById(R.id.video3).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        NRLog.d("Button Pressed Tag = " + v.getTag());

        Intent intent = new Intent(this, Exo2Activity.class);
        intent.putExtra("video", (String)v.getTag());
        startActivity(intent);
    }
}
