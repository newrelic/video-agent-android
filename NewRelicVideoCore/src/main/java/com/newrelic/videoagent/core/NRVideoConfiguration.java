package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.harvest.HarvestCallback;

/**
 * Unified configuration for New Relic Video Agent
 * Merges video tracking and harvest settings into a single, flexible configuration
 * Uses Builder pattern for clean API and optional parameters
 */
public final class NRVideoConfiguration {

    // Core settings
    private final String applicationToken;
    private final String endpointUrl;
    private final String region;

    // Video tracking settings
    private final boolean adTrackingEnabled;
    private final boolean autoStartTracking;

    // Harvest settings
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int maxBatchSizeBytes;
    private final int maxDeadLetterSize;
    private final long deadLetterRetryInterval;
    private final boolean memoryOptimized;

    // Advanced settings
    private final HarvestCallback harvestCallback;
    private final boolean debugLoggingEnabled;

    private NRVideoConfiguration(Builder builder) {
        this.applicationToken = builder.applicationToken;
        this.endpointUrl = builder.endpointUrl;
        this.region = builder.region;
        this.adTrackingEnabled = builder.adTrackingEnabled;
        this.autoStartTracking = builder.autoStartTracking;
        this.harvestCycleSeconds = builder.harvestCycleSeconds;
        this.liveHarvestCycleSeconds = builder.liveHarvestCycleSeconds;
        this.maxBatchSizeBytes = builder.maxBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.deadLetterRetryInterval = builder.deadLetterRetryInterval;
        this.memoryOptimized = builder.memoryOptimized;
        this.harvestCallback = builder.harvestCallback;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
    }

    // Getters
    public String getApplicationToken() { return applicationToken; }
    public String getLicenseKey() { return applicationToken; } // Alias for compatibility
    public String getEndpointUrl() { return endpointUrl; }
    public String getRegion() { return region; }
    public boolean isAdTrackingEnabled() { return adTrackingEnabled; }
    public boolean isAutoStartTracking() { return autoStartTracking; }
    public int getHarvestCycleSeconds() { return harvestCycleSeconds; }
    public int getLiveHarvestCycleSeconds() { return liveHarvestCycleSeconds; }
    public int getMaxBatchSizeBytes() { return maxBatchSizeBytes; }
    public int getMaxDeadLetterSize() { return maxDeadLetterSize; }
    public long getDeadLetterRetryInterval() { return deadLetterRetryInterval; }
    public boolean isMemoryOptimized() { return memoryOptimized; }
    public HarvestCallback getHarvestCallback() { return harvestCallback; }
    public boolean isDebugLoggingEnabled() { return debugLoggingEnabled; }

    /**
     * Builder for NRVideoConfiguration - provides flexible, readable API
     */
    public static class Builder {
        // Required parameters
        private final String applicationToken;

        // Optional parameters with defaults
        private String endpointUrl;
        private String region = "US";
        private boolean adTrackingEnabled = true;
        private boolean autoStartTracking = true;
        private int harvestCycleSeconds = 60;
        private int liveHarvestCycleSeconds = 10;
        private int maxBatchSizeBytes = 8192;
        private int maxDeadLetterSize = 1000;
        private long deadLetterRetryInterval = 300000; // 5 minutes
        private boolean memoryOptimized = true;
        private HarvestCallback harvestCallback;
        private boolean debugLoggingEnabled = false;

        public Builder(String applicationToken) {
            if (applicationToken == null || applicationToken.trim().isEmpty()) {
                throw new IllegalArgumentException("Application token is required");
            }
            this.applicationToken = applicationToken;
            // Auto-generate endpoint URL based on region
            this.endpointUrl = generateEndpointUrl(region);
        }

        public Builder region(String region) {
            this.region = region;
            this.endpointUrl = generateEndpointUrl(region);
            return this;
        }

        public Builder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        public Builder enableAdTracking(boolean enabled) {
            this.adTrackingEnabled = enabled;
            return this;
        }

        public Builder autoStartTracking(boolean enabled) {
            this.autoStartTracking = enabled;
            return this;
        }

        public Builder harvestCycle(int seconds) {
            if (seconds < 5) throw new IllegalArgumentException("Harvest cycle must be at least 5 seconds");
            this.harvestCycleSeconds = seconds;
            return this;
        }

        public Builder liveHarvestCycle(int seconds) {
            if (seconds < 1) throw new IllegalArgumentException("Live harvest cycle must be at least 1 second");
            this.liveHarvestCycleSeconds = seconds;
            return this;
        }

        public Builder maxBatchSize(int bytes) {
            if (bytes < 1024) throw new IllegalArgumentException("Max batch size must be at least 1KB");
            this.maxBatchSizeBytes = bytes;
            return this;
        }

        public Builder maxDeadLetterSize(int size) {
            if (size < 100) throw new IllegalArgumentException("Max dead letter size must be at least 100");
            this.maxDeadLetterSize = size;
            return this;
        }

        public Builder deadLetterRetryInterval(long milliseconds) {
            if (milliseconds < 10000) throw new IllegalArgumentException("Retry interval must be at least 10 seconds");
            this.deadLetterRetryInterval = milliseconds;
            return this;
        }

        public Builder memoryOptimized(boolean enabled) {
            this.memoryOptimized = enabled;
            return this;
        }

        public Builder harvestCallback(HarvestCallback callback) {
            this.harvestCallback = callback;
            return this;
        }

        public Builder debugLogging(boolean enabled) {
            this.debugLoggingEnabled = enabled;
            return this;
        }

        public NRVideoConfiguration build() {
            return new NRVideoConfiguration(this);
        }

        private String generateEndpointUrl(String region) {
            // Integrated domain selection - no need for separate ApiDomainSelector
            String primaryDomain;
            switch (region.toUpperCase()) {
                case "US":
                    primaryDomain = "mobile-collector.newrelic.com";
                    break;
                case "EU":
                    primaryDomain = "mobile-collector.eu.newrelic.com";
                    break;
                default:
                    primaryDomain = "mobile-collector.newrelic.com";
                    break;
            }
            return "https://" + primaryDomain + "/mobile/v1/events";
        }
    }

    /**
     * Create a minimal configuration with just the application token
     */
    public static NRVideoConfiguration minimal(String applicationToken) {
        return new Builder(applicationToken).build();
    }

    /**
     * Create a live streaming optimized configuration
     */
    public static NRVideoConfiguration forLiveStreaming(String applicationToken) {
        return new Builder(applicationToken)
            .liveHarvestCycle(5)    // Faster live event processing
            .harvestCycle(30)       // More frequent regular harvests
            .maxBatchSize(4096)     // Smaller batches for speed
            .memoryOptimized(true)
            .build();
    }

    /**
     * Create an on-demand content optimized configuration
     */
    public static NRVideoConfiguration forOnDemandContent(String applicationToken) {
        return new Builder(applicationToken)
            .liveHarvestCycle(15)   // Less frequent live processing
            .harvestCycle(90)       // Larger harvest intervals
            .maxBatchSize(16384)    // Larger batches for efficiency
            .memoryOptimized(true)
            .build();
    }
}
