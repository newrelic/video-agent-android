package com.newrelic.videoagent.core;

import static com.newrelic.videoagent.core.BuildConfig.*;

public class NRDef {
    private NRDef() {
    }

    public static final String NRVIDEO_CORE_VERSION = VERSION_NAME;

    public static final String NR_VIDEO_EVENT = "VideoAction";
    public static final String NR_VIDEO_AD_EVENT = "VideoAdAction";
    public static final String NR_VIDEO_ERROR_EVENT = "VideoErrorAction";
    public static final String NR_VIDEO_CUSTOM_EVENT = "VideoCustomAction";

    public static final String SRC = "ANDROID";

    public static final String SRC = "ANDROID";

    public static final String TRACKER_READY = "TRACKER_READY";
    public static final String PLAYER_READY = "PLAYER_READY";

    public static final String CONTENT_REQUEST = "CONTENT_REQUEST";
    public static final String CONTENT_START = "CONTENT_START";
    public static final String CONTENT_PAUSE = "CONTENT_PAUSE";
    public static final String CONTENT_RESUME = "CONTENT_RESUME";
    public static final String CONTENT_END = "CONTENT_END";
    public static final String CONTENT_SEEK_START = "CONTENT_SEEK_START";
    public static final String CONTENT_SEEK_END = "CONTENT_SEEK_END";
    public static final String CONTENT_BUFFER_START = "CONTENT_BUFFER_START";
    public static final String CONTENT_BUFFER_END = "CONTENT_BUFFER_END";
    public static final String CONTENT_HEARTBEAT = "CONTENT_HEARTBEAT";
    public static final String CONTENT_RENDITION_CHANGE = "CONTENT_RENDITION_CHANGE";
    public static final String CONTENT_ERROR = "CONTENT_ERROR";

    public static final String AD_REQUEST = "AD_REQUEST";
    public static final String AD_START = "AD_START";
    public static final String AD_PAUSE = "AD_PAUSE";
    public static final String AD_RESUME = "AD_RESUME";
    public static final String AD_END = "AD_END";
    public static final String AD_SEEK_START = "AD_SEEK_START";
    public static final String AD_SEEK_END = "AD_SEEK_END";
    public static final String AD_BUFFER_START = "AD_BUFFER_START";
    public static final String AD_BUFFER_END = "AD_BUFFER_END";
    public static final String AD_HEARTBEAT = "AD_HEARTBEAT";
    public static final String AD_RENDITION_CHANGE = "AD_RENDITION_CHANGE";
    public static final String AD_ERROR = "AD_ERROR";
    public static final String AD_BREAK_START = "AD_BREAK_START";
    public static final String AD_BREAK_END = "AD_BREAK_END";
    public static final String AD_QUARTILE = "AD_QUARTILE";
    public static final String AD_CLICK = "AD_CLICK";
}
