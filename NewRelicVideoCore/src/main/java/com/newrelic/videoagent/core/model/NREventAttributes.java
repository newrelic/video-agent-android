package com.newrelic.videoagent.core.model;

import com.newrelic.videoagent.core.utils.NRLog;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Event attributes model.
 */
public class NREventAttributes {

    private Map<String, Map<String, Object>> attributeBuckets;

    // --- Standard Event Attribute Names (Constants) ---
    public static final String VIDEO_EVENT_TYPE = "videoEventType"; // Generic event type for video events
    public static final String VIDEO_SESSION_ID = "videoSessionId";
    public static final String PLAYER_NAME = "playerName";
    public static final String ACCOUNT_ID = "accountId";
    public static final String APP_NAME = "appName";
    public static final String CONTENT_ID = "contentId";
    public static final String CONTENT_TITLE = "contentTitle";
    public static final String CUSTOM_ATTRIBUTES = "customAttributes"; // For nested custom attributes

    // Playback-related attributes
    public static final String DURATION = "duration";
    public static final String PLAYHEAD_POSITION = "playheadPosition";
    public static final String BITRATE = "bitrate";
    public static final String RENDERED_FRAMERATE = "renderedFramerate";
    public static final String RESOLUTION = "resolution"; // e.g., "1920x1080"
    public static final String FULLSCREEN = "fullscreen";
    public static final String PLAYER_VERSION = "playerVersion";
    public static final String STREAM_TYPE = "streamType"; // VOD, Live
    public static final String HAS_ADS = "hasAds";
    public static final String IS_MUTED = "isMuted";
    public static final String TOTAL_AD_PLAYBACK_DURATION = "totalAdPlaybackDuration";
    public static final String TOTAL_CONTENT_PLAYBACK_DURATION = "totalContentPlaybackDuration";

    // Error/buffering attributes
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String BUFFER_TIME = "bufferTime";
    public static final String DROPPED_FRAMES = "droppedFrames";

    // Ad-specific attributes
    public static final String AD_ID = "adId";
    public static final String AD_TITLE = "adTitle";
    public static final String AD_POSITION = "adPosition"; // Pre-roll, Mid-roll, Post-roll
    public static final String AD_CREATIVE_ID = "adCreativeId";
    public static final String AD_PLAYBACK_DURATION = "adPlaybackDuration";

    // Quality of Experience (QoE)
    public static final String REBUFFERING_RATE = "rebufferRate";
    public static final String REBUFFERING_COUNT = "rebufferCount";
    public static final String STARTUP_TIME = "startupTime";

    // User interaction
    public static final String USER_ID = "enduser.id"; // Standard New Relic user ID attribute

    // --- Event Types (Values for VIDEO_EVENT_TYPE) ---
    public static final String EVENT_TYPE_PLAY = "Play";
    public static final String EVENT_TYPE_PAUSE = "Pause";
    public static final String EVENT_TYPE_RESUME = "Resume";
    public static final String EVENT_TYPE_BUFFER_START = "BufferStart";
    public static final String EVENT_TYPE_BUFFER_END = "BufferEnd";
    public static final String EVENT_TYPE_ERROR = "Error";
    public static final String EVENT_TYPE_END = "End";
    public static final String EVENT_TYPE_READY = "Ready";
    public static final String EVENT_TYPE_SEEK_START = "SeekStart";
    public static final String EVENT_TYPE_SEEK_END = "SeekEnd";
    public static final String EVENT_TYPE_AD_START = "AdStart";
    public static final String EVENT_TYPE_AD_END = "AdEnd";
    public static final String EVENT_TYPE_VIDEO_HEARTBEAT = "VideoHeartbeat"; // Periodic updates


    /**
     * Initializes a new event attributes model.
     * This constructor creates the internal map to store attribute buckets.
     */
    public NREventAttributes() {
        attributeBuckets = new HashMap<>();
        NRLog.d("NREventAttributes initialized.");
    }

    /**
     * Set attribute for a given action filter.
     * Attributes set with a filter will only be applied to events whose action name
     * matches the specified filter (regular expression).
     *
     * @param key Attribute name.
     * @param value Attribute value.
     * @param filter Action filter, a regular expression. If null, a universal filter `[A-Z_]+` is used.
     */
    public void setAttribute(String key, Object value, String filter) {
        // If no filter defined, use a universal filter that matches any action name
        if (filter == null || filter.isEmpty()) {
            filter = "[A-Z_]+"; // Default filter to match any uppercase, underscore action
            NRLog.d("No filter provided for attribute '" + key + "'. Using default universal filter: " + filter);
        }

        // If the attribute bucket for this filter doesn't exist yet, create it.
        attributeBuckets.computeIfAbsent(filter, k -> new HashMap<>()).put(key, value);
        NRLog.d("Set attribute for filter '" + filter + "': " + key + " = " + value);
    }

    /**
     * Generate list of attributes for a given action.
     * This method combines a base set of attributes with dynamically applied attributes
     * from buckets whose filters match the provided action name.
     *
     * @param action The action name to match against attribute filters.
     * @param baseAttributes Optional. A map of attributes to append to. Can be null.
     * @return A new Map of combined attributes, or an empty map if no attributes are generated.
     */
    public Map<String, Object> generateAttributes(String action, Map<String, Object> baseAttributes) {
        Map<String, Object> finalAttributes = new HashMap<>();

        // Start with the base attributes if provided
        if (baseAttributes != null) {
            finalAttributes.putAll(baseAttributes);
        }

        // Iterate through all attribute buckets and apply attributes if the filter matches the action
        for (Map.Entry<String, Map<String, Object>> pair : attributeBuckets.entrySet()) {
            String filter = pair.getKey();
            if (checkFilter(filter, action)) {
                Map<String, Object> bucket = pair.getValue();
                // Put all attributes from the matching bucket into the final map.
                // Existing keys will be overwritten by attributes from buckets.
                finalAttributes.putAll(bucket);
                NRLog.d("Applied attributes from filter '" + filter + "' for action '" + action + "'.");
            }
        }

        return finalAttributes;
    }

    /**
     * Checks if an action name matches a given regular expression filter.
     *
     * @param filter The regular expression string.
     * @param action The action name to check.
     * @return true if the action matches the filter, false otherwise.
     */
    private boolean checkFilter(String filter, String action) {
        if (action == null || action.isEmpty()) {
            return false;
        }
        try {
            return Pattern.matches(filter, action);
        } catch (java.util.regex.PatternSyntaxException e) {
            NRLog.e("Invalid regex filter pattern: " + filter + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Provides a string representation of the NREventAttributes object,
     * primarily showing the contents of the attribute buckets.
     *
     * @return A string representation of this object.
     */
    @Override
    public String toString() {
        return "NREventAttributes = " + attributeBuckets;
    }
}
