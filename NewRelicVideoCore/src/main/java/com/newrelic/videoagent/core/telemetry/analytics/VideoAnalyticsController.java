package com.newrelic.videoagent.core.telemetry.analytics;

import com.newrelic.videoagent.core.utils.NRLog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VideoAnalyticsController {

    private static VideoAnalyticsController instance;

    // Attributes are stored in a thread-safe map.
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    // Event queue for storing custom events before they are harvested.
    // ConcurrentLinkedQueue is suitable for high-throughput scenarios where ordering is important
    // and null elements are not allowed.
    private final ConcurrentLinkedQueue<Map<String, Object>> eventQueue = new ConcurrentLinkedQueue<>();

    // Private constructor to enforce Singleton pattern.
    private VideoAnalyticsController() {
        NRLog.d("VideoAnalyticsController initialized.");
    }

    /**
     * Returns the singleton instance of the VideoAnalyticsController.
     * Uses double-checked locking for thread-safe lazy initialization.
     * @return The singleton instance of VideoAnalyticsController.
     */
    public static VideoAnalyticsController getInstance() {
        if (instance == null) {
            synchronized (VideoAnalyticsController.class) {
                if (instance == null) {
                    instance = new VideoAnalyticsController();
                }
            }
        }
        return instance;
    }

    /**
     * Sets an attribute value of any Object type. This method provides flexibility
     * for various data types, but callers should ensure the object is serializable
     * to JSON.
     * @param name The attribute name.
     * @param value The attribute value (can be String, Number, Boolean, or other JSON-compatible objects).
     * @return true if successful, false otherwise.
     */
    public boolean setAttribute(String name, Object value) {
        if (name == null || name.isEmpty()) {
            NRLog.e("Attribute name cannot be null or empty.");
            return false;
        }
        attributes.put(name, value);
        NRLog.d("Set attribute: " + name + " = " + value + " (Type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
        return true;
    }

    /**
     * Sets a string attribute value.
     * @param name The attribute name.
     * @param value The string attribute value.
     * @return true if successful, false otherwise.
     */
    public boolean setAttribute(String name, String value) {
        // Delegates to the generic setAttribute(String, Object) for consistency.
        return setAttribute(name, (Object) value);
    }

    /**
     * Sets a numeric double attribute value.
     * @param name The attribute name.
     * @param value The double attribute value.
     * @return true if successful, false otherwise.
     */
    public boolean setAttribute(String name, double value) {
        // Delegates to the generic setAttribute(String, Object) for consistency.
        return setAttribute(name, (Object) value);
    }

    /**
     * Sets a boolean attribute value.
     * @param name The attribute name.
     * @param value The boolean attribute value.
     * @return true if successful, false otherwise.
     */
    public boolean setAttribute(String name, boolean value) {
        // Delegates to the generic setAttribute(String, Object) for consistency.
        return setAttribute(name, (Object) value);
    }

    /**
     * Increments the value of a numeric attribute by 1.0. If the attribute does not exist
     * or is not numeric, it will be set to 1.0.
     * @param name The attribute name.
     * @return true if successful, false otherwise.
     */
    public boolean incrementAttribute(String name) {
        return incrementAttribute(name, 1.0);
    }

    /**
     * Increments the value of a numeric attribute by a specified amount.
     * If the attribute does not exist or is not numeric, it will be set to the increment value.
     * @param name The attribute name.
     * @param value The amount by which to increment the attribute.
     * @return true if successful, false otherwise.
     */
    public boolean incrementAttribute(String name, double value) {
        if (name == null || name.isEmpty()) {
            NRLog.e("Attribute name cannot be null or empty.");
            return false;
        }

        attributes.compute(name, (key, existingValue) -> {
            if (existingValue instanceof Number) {
                return ((Number) existingValue).doubleValue() + value;
            } else {
                return value; // If not a number, set to the increment value
            }
        });
        NRLog.d("Incremented attribute: " + name + " by " + value + ". New value: " + attributes.get(name));
        return true;
    }

    /**
     * Removes an attribute.
     * @param name The attribute name.
     * @return true if the attribute was removed, false otherwise.
     */
    public boolean removeAttribute(String name) {
        if (name == null || name.isEmpty()) {
            NRLog.e("Attribute name cannot be null or empty.");
            return false;
        }
        boolean removed = attributes.remove(name) != null;
        if (removed) {
            NRLog.d("Removed attribute: " + name);
        } else {
            NRLog.d("Attribute not found for removal: " + name);
        }
        return removed;
    }

    /**
     * Removes all accumulated attributes.
     * @return true if successful, false otherwise.
     */
    public boolean removeAllAttributes() {
        attributes.clear();
        NRLog.d("All attributes removed.");
        return true;
    }

    /**
     * Records a custom analytic event with a specified eventType.
     * Automatically includes all currently set attributes.
     * @param eventType The name of the custom event type.
     * @param eventAttributes A map of key-value pairs holding additional event-specific attributes.
     * Values should be of type String, Double, or Boolean.
     * @return true if the event was successfully queued, false otherwise.
     */
    public boolean recordCustomEvent(String eventType, Map<String, Object> eventAttributes) {
        if (eventType == null || eventType.isEmpty()) {
            NRLog.e("Event type cannot be null or empty.");
            return false;
        }

        // Create a new map for the event, including all current global attributes and specific event attributes.
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.putAll(attributes); // Add all current attributes
        if (eventAttributes != null) {
            event.putAll(eventAttributes);
        }

        eventQueue.add(event);
        NRLog.d("Recorded custom event: " + eventType + " with attributes: " + event);
        return true;
    }

    /**
     * Records a custom analytic event with a specified eventType and an optional eventName.
     * Automatically includes all currently set attributes.
     * @param eventType The name of the custom event type.
     * @param eventName An optional name for the specific event instance.
     * @param eventAttributes A map of key-value pairs holding additional event-specific attributes.
     * @return true if the event was successfully queued, false otherwise.
     */
    public boolean recordCustomEvent(String eventType, String eventName, Map<String, Object> eventAttributes) {
        Map<String, Object> combinedAttributes = new HashMap<>();
        if (eventAttributes != null) {
            combinedAttributes.putAll(eventAttributes);
        }
        if (eventName != null && !eventName.isEmpty()) {
            combinedAttributes.put("eventName", eventName); // Use "eventName" as a standard key
        }
        return recordCustomEvent(eventType, combinedAttributes);
    }

    /**
     * Retrieves a read-only map of all currently set global attributes.
     * @return A map of attributes.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Retrieves the queue of events that are ready to be harvested.
     * This method is intended to be called by the harvesting mechanism.
     * @return A ConcurrentLinkedQueue containing maps of event data.
     */
    public ConcurrentLinkedQueue<Map<String, Object>> getEventQueue() {
        return eventQueue;
    }
}
