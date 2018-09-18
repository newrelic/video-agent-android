package com.newrelic.videoagent.backend;

public class EventDefs {
    public static String VIDEO_EVENT                 = "VideoEvent";
    public static String CONTENT_REQUEST             = "CONTENT_REQUEST";
    public static String CONTENT_START               = "CONTENT_START";
    public static String CONTENT_END                 = "CONTENT_END";
    public static String CONTENT_PAUSE               = "CONTENT_PAUSE";
    public static String CONTENT_RESUME              = "CONTENT_RESUME";
    public static String CONTENT_SEEK_START          = "CONTENT_SEEK_START";
    public static String CONTENT_SEEK_END            = "CONTENT_SEEK_END";
    public static String CONTENT_BUFFER_START        = "CONTENT_BUFFER_START";
    public static String CONTENT_BUFFER_END          = "CONTENT_BUFFER_END";
    public static String CONTENT_HEARTBEAT           = "CONTENT_HEARTBEAT";
    public static String CONTENT_RENDITION_CHANGE    = "CONTENT_RENDITION_CHANGE";
    public static String CONTENT_ERROR               = "CONTENT_ERROR";

    public static String AD_REQUEST                  = "AD_REQUEST";
    public static String AD_START                    = "AD_START";
    public static String AD_END                      = "AD_END";
    public static String AD_PAUSE                    = "AD_PAUSE";
    public static String AD_RESUME                   = "AD_RESUME";
    public static String AD_SEEK_START               = "AD_SEEK_START";
    public static String AD_SEEK_END                 = "AD_SEEK_END";
    public static String AD_BUFFER_START             = "AD_BUFFER_START";
    public static String AD_BUFFER_END               = "AD_BUFFER_END";
    public static String AD_HEARTBEAT                = "AD_HEARTBEAT";
    public static String AD_RENDITION_CHANGE         = "AD_RENDITION_CHANGE";
    public static String AD_ERROR                    = "AD_ERROR";
    public static String AD_BREAK_START              = "AD_BREAK_START";
    public static String AD_BREAK_END                = "AD_BREAK_END";
    public static String AD_QUARTILE                 = "AD_QUARTILE";
    public static String AD_CLICK                    = "AD_CLICK";

    public static String PLAYER_READY                = "PLAYER_READY";
    public static String DOWNLOAD                    = "DOWNLOAD";
}
