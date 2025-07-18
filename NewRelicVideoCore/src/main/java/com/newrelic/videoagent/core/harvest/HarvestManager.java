package com.newrelic.videoagent.core.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HarvestManager {
    private static HarvestManager instance;
    private final EventBuffer eventBuffer = new EventBuffer();
    private final SizeEstimator sizeEstimator = new DefaultSizeEstimator();
    private final ConcurrentLinkedQueue<Map<String, Object>> immediateHarvestQueue = new ConcurrentLinkedQueue<>();
    private HarvestScheduler harvestScheduler;
    private String appToken;
    private int harvestCycleSeconds = 60; // Default cycle
    private String endpointUrl;

    private static final int MAX_BATCH_SIZE_BYTES = 1048576; // 1MB

    private HarvestManager() {}

    public static HarvestManager getInstance() {
        if (instance == null) {
            instance = new HarvestManager();
        }
        return instance;
    }

    public interface HarvestCallback {
        void beforeHarvest(List<Map<String, Object>> events);
        void afterHarvest(List<Map<String, Object>> events, boolean success, Exception error);
    }

    private HarvestCallback harvestCallback;
    private String region = "US";
    private String apiDomain;

    public void setHarvestCallback(HarvestCallback callback) {
        this.harvestCallback = callback;
    }

    public void setRegion(String region) {
        this.region = region;
        this.apiDomain = selectApiDomain(region);
    }

    private String selectApiDomain(String region) {
        switch (region.toUpperCase()) {
            case "EU":
                return "https://eu-api.newrelic.com";
            case "US":
            default:
                return "https://api.newrelic.com";
        }
    }

    // Remove lifecycle registration from init
    public void init(String licenseKey, String endpointUrl, int harvestCycleSeconds, String region) {
        this.region = region;
        this.apiDomain = selectApiDomain(region);
        this.appToken = generateAppToken(licenseKey, region);
        this.endpointUrl = endpointUrl;
        this.harvestCycleSeconds = harvestCycleSeconds;
        startScheduler(harvestCycleSeconds);
    }

    public void startScheduler(int harvestCycleSeconds) {
        if (harvestScheduler == null) {
            harvestScheduler = new HarvestScheduler(this::harvest, harvestCycleSeconds);
            harvestScheduler.start();
        }
    }

    public void shutdownScheduler() {
        if (harvestScheduler != null) {
            harvestScheduler.shutdown();
            harvestScheduler = null;
        }
    }

    private String generateAppToken(String licenseKey, String region) {
        // Simulate token generation (replace with real logic if needed)
        return Integer.toHexString((licenseKey + region).hashCode());
    }

    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        List<Map<String, Object>> harvestBatch = new ArrayList<>();
        // Before harvest callback
        if (harvestCallback != null) {
            harvestCallback.beforeHarvest(harvestBatch);
        }
        boolean harvestTriggered = eventBuffer.addEventOrHarvest(attributes, MAX_BATCH_SIZE_BYTES, sizeEstimator, harvestBatch);
        if (harvestTriggered && !harvestBatch.isEmpty()) {
            boolean success = false;
            Exception error = null;
            try {
                sendBatch(harvestBatch);
                success = true;
            } catch (Exception e) {
                error = e;
            }
            // After harvest callback
            if (harvestCallback != null) {
                harvestCallback.afterHarvest(harvestBatch, success, error);
            }
        }
        // Immediate harvest if buffer size exceeds batch size (for non-full cases)
        if (eventBuffer.getSize(sizeEstimator) >= MAX_BATCH_SIZE_BYTES) {
            List<Map<String, Object>> batch = eventBuffer.pollBatch(MAX_BATCH_SIZE_BYTES, sizeEstimator);
            if (!batch.isEmpty()) {
                boolean success = false;
                Exception error = null;
                try {
                    sendBatch(batch);
                    success = true;
                } catch (Exception e) {
                    error = e;
                }
                if (harvestCallback != null) {
                    harvestCallback.afterHarvest(batch, success, error);
                }
            }
        }
    }

    private int estimateEventSizeBytes(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String) return ((String) obj).getBytes().length;
        if (obj instanceof Number) return 8;
        if (obj instanceof Boolean) return 1;
        if (obj instanceof Map) {
            int size = 0;
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                size += estimateEventSizeBytes(entry.getKey());
                size += estimateEventSizeBytes(entry.getValue());
            }
            return size;
        }
        if (obj instanceof List) {
            int size = 0;
            for (Object item : (List<?>) obj) {
                size += estimateEventSizeBytes(item);
            }
            return size;
        }
        return 16; // Default for other types
    }

    private int getBufferSizeBytes() {
        int size = 0;
        for (Map<String, Object> event : immediateHarvestQueue) {
            size += estimateEventSizeBytes(event);
        }
        return size;
    }

    private void harvestImmediate() {
        List<Map<String, Object>> eventsToSend = new ArrayList<>();
        int batchSize = 0;
        while (!immediateHarvestQueue.isEmpty() && batchSize < MAX_BATCH_SIZE_BYTES) {
            Map<String, Object> event = immediateHarvestQueue.peek();
            int eventSize = estimateEventSizeBytes(event);
            if (batchSize + eventSize > MAX_BATCH_SIZE_BYTES) break;
            eventsToSend.add(immediateHarvestQueue.poll());
            batchSize += eventSize;
        }
        sendBatch(eventsToSend);
    }

    public void harvest() {
        if (eventBuffer.isEmpty()) return;
        List<Map<String, Object>> batch = eventBuffer.pollBatch(MAX_BATCH_SIZE_BYTES, sizeEstimator);
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    private void sendBatch(List<Map<String, Object>> eventsToSend) {
        // Centralized API endpoint logic
        String url = apiDomain + endpointUrl;
        // TODO: Implement HTTP POST using appToken
        System.out.println("Harvesting " + eventsToSend.size() + " events (" + getBatchSizeBytes(eventsToSend) + " bytes) to " + url + " with token " + appToken);
    }

    private int getBatchSizeBytes(List<Map<String, Object>> batch) {
        int size = 0;
        for (Map<String, Object> event : batch) {
            size += estimateEventSizeBytes(event);
        }
        return size;
    }

    public void shutdown() {
        if (harvestScheduler != null) {
            harvestScheduler.shutdown();
        }
    }

    public static class Config {
        public String licenseKey;
        public String endpointUrl;
        public int harvestCycleSeconds = 60;
        public String region = "US";
        public HarvestCallback harvestCallback;
        public Config(String licenseKey, String endpointUrl) {
            this.licenseKey = licenseKey;
            this.endpointUrl = endpointUrl;
        }
        public Config setHarvestCycleSeconds(int seconds) {
            this.harvestCycleSeconds = seconds;
            return this;
        }
        public Config setRegion(String region) {
            this.region = region;
            return this;
        }
        public Config setHarvestCallback(HarvestCallback callback) {
            this.harvestCallback = callback;
            return this;
        }
    }

    public void configure(Config config) {
        this.region = config.region;
        this.apiDomain = selectApiDomain(config.region);
        this.appToken = generateAppToken(config.licenseKey, config.region);
        this.endpointUrl = config.endpointUrl;
        this.harvestCycleSeconds = config.harvestCycleSeconds;
        this.harvestCallback = config.harvestCallback;
        startScheduler(harvestCycleSeconds);
    }
}
