package com.newrelic.videoagent;

import android.net.Uri;

import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;

import java.util.List;

public abstract class TrackerBuilder {

    public abstract ContentsTracker contents();

    public abstract AdsTracker ads();

    public abstract void startWithPlayer(Object player, Uri videoUri);

    public abstract void startWithPlayer(Object player, List<Uri> playlist);
}
