package com.newrelic.videoagent.backend;

public enum EventDefs {
    VIDEO_EVENT                 ("VideoEvent"),
    CONTENT_REQUEST             ("CONTENT_REQUEST"),
    CONTENT_START               ("CONTENT_START"),
    CONTENT_END                 ("CONTENT_END"),
    CONTENT_PAUSE               ("CONTENT_PAUSE"),
    CONTENT_RESUME              ("CONTENT_RESUME"),
    CONTENT_SEEK_START          ("CONTENT_SEEK_START"),
    CONTENT_SEEK_END            ("CONTENT_SEEK_END"),
    CONTENT_BUFFER_START        ("CONTENT_BUFFER_START"),
    CONTENT_BUFFER_END          ("CONTENT_BUFFER_END"),
    CONTENT_HEARTBEAT           ("CONTENT_HEARTBEAT"),
    CONTENT_RENDITION_CHANGE    ("CONTENT_RENDITION_CHANGE"),
    CONTENT_ERROR               ("CONTENT_ERROR"),

    AD_REQUEST                  ("AD_REQUEST"),
    AD_START                    ("AD_START"),
    AD_END                      ("AD_END"),
    AD_PAUSE                    ("AD_PAUSE"),
    AD_RESUME                   ("AD_RESUME"),
    AD_SEEK_START               ("AD_SEEK_START"),
    AD_SEEK_END                 ("AD_SEEK_END"),
    AD_BUFFER_START             ("AD_BUFFER_START"),
    AD_BUFFER_END               ("AD_BUFFER_END"),
    AD_HEARTBEAT                ("AD_HEARTBEAT"),
    AD_RENDITION_CHANGE         ("AD_RENDITION_CHANGE"),
    AD_ERROR                    ("AD_ERROR"),
    AD_BREAK_START              ("AD_BREAK_START"),
    AD_BREAK_END                ("AD_BREAK_END"),
    AD_QUARTILE                 ("AD_QUARTILE"),
    AD_CLICK                    ("AD_CLICK"),

    PLAYER_READY                ("PLAYER_READY"),
    DOWNLOAD                    ("DOWNLOAD")
    ;

    private final String text;

    /**
     * @param text
     */
    EventDefs(final String text) {
        this.text = text;
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return text;
    }
}
