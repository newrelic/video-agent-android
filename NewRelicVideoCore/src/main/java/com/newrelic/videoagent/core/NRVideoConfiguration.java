package com.newrelic.videoagent.core;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Optimized configuration for New Relic Video Agent
 * Android Mobile & TV optimized with automatic device detection and player support
 */
public final class NRVideoConfiguration {
    private final String applicationToken;
    private final String endpointUrl;
    private final String region;
    private final boolean adTrackingEnabled;
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int maxBatchSizeBytes;
    private final int maxDeadLetterSize;
    private final boolean memoryOptimized;
    private final boolean debugLoggingEnabled;
    private final boolean enableCrashSafety;
    private final boolean isTV;

    private NRVideoConfiguration(Builder builder) {
        this.applicationToken = builder.applicationToken;
        this.region = identifyRegion();
        this.endpointUrl = generateEndpointUrl();
        this.adTrackingEnabled = builder.adTrackingEnabled;
        this.harvestCycleSeconds = builder.harvestCycleSeconds;
        this.liveHarvestCycleSeconds = builder.liveHarvestCycleSeconds;
        this.maxBatchSizeBytes = builder.maxBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.memoryOptimized = builder.memoryOptimized;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
        this.enableCrashSafety = builder.enableCrashSafety;
        this.isTV = builder.isTV;
    }

    // Getters
    public String getApplicationToken() { return applicationToken; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getRegion() { return region; }
    public boolean isAdTrackingEnabled() { return adTrackingEnabled; }
    public int getHarvestCycleSeconds() { return harvestCycleSeconds; }
    public int getLiveHarvestCycleSeconds() { return liveHarvestCycleSeconds; }
    public int getMaxBatchSizeBytes() { return maxBatchSizeBytes; }
    public int getMaxDeadLetterSize() { return maxDeadLetterSize; }
    public boolean isMemoryOptimized() { return memoryOptimized; }
    public boolean isDebugLoggingEnabled() { return debugLoggingEnabled; }
    public boolean isCrashSafety() {
        return enableCrashSafety;
    }
    public boolean isTV() { return isTV; }

    /**
     * Get dead letter retry interval in milliseconds
     * Default retry interval for failed events
     */
    public long getDeadLetterRetryInterval() {
        // Base retry interval - 60 seconds for video streaming scenarios
        return 60000L;
    }

    private String identifyRegion() {
        return "US";
    }
    private String generateEndpointUrl() {
        String domain = "EU".equalsIgnoreCase(region) ?
                "mobile-collector.eu.newrelic.com" :
                "mobile-collector.newrelic.com";
        return "https://" + domain + "/mobile/v1/events";
    }

    /**
     * Create optimal configuration with automatic device detection
     */
    public static NRVideoConfiguration createOptimal(String applicationToken, Context context) {
        if (applicationToken == null || applicationToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Application token is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context is required for device detection");
        }

        Builder builder = new Builder(applicationToken);
        return builder.buildOptimal(context);
    }

    /**
     * Builder with Android Mobile & TV optimized defaults and auto-detection
     */
    public static class Builder {
        private final String applicationToken;
        private boolean adTrackingEnabled = false;  // Disabled by default, user must explicitly enable
        private int harvestCycleSeconds = 90;
        private int liveHarvestCycleSeconds = 45;
        private int maxBatchSizeBytes = 4096;
        private int maxDeadLetterSize = 500;
        private boolean memoryOptimized = true;
        private boolean debugLoggingEnabled = false;
        private boolean enableCrashSafety = true; // Always enabled for crash safety
        private boolean isTV = false;

        public Builder(String applicationToken) {
            if (applicationToken == null || applicationToken.trim().isEmpty()) {
                throw new IllegalArgumentException("Application token is required");
            }
            this.applicationToken = applicationToken;
        }

        public Builder enableAdTracking(boolean enabled) {
            this.adTrackingEnabled = enabled;
            return this;
        }

        public Builder harvestCycle(int seconds) {
            if (seconds < 30) throw new IllegalArgumentException("Harvest cycle must be at least 30 seconds");
            this.harvestCycleSeconds = seconds;
            return this;
        }

        public Builder liveHarvestCycle(int seconds) {
            if (seconds < 10) throw new IllegalArgumentException("Live harvest cycle must be at least 5 seconds");
            this.liveHarvestCycleSeconds = seconds;
            return this;
        }

        public Builder maxBatchSize(int bytes) {
            if (bytes < 1024) throw new IllegalArgumentException("Max batch size must be at least 1KB");
            this.maxBatchSizeBytes = bytes;
            return this;
        }

        public Builder maxDeadLetterSize(int size) {
            if (size < 50) throw new IllegalArgumentException("Max dead letter size must be at least 50");
            this.maxDeadLetterSize = size;
            return this;
        }

        public Builder memoryOptimized(boolean enabled) {
            this.memoryOptimized = enabled;
            return this;
        }

        public Builder enableDebugLogging() {
            this.debugLoggingEnabled = true;
            return this;
        }

        public Builder disableCrashSafety() {
            this.enableCrashSafety = false;
            return this;
        }

        private Builder isTV(boolean isTV) {
            this.isTV = isTV;
            return this;
        }

        public NRVideoConfiguration build() {
            return new NRVideoConfiguration(this);
        }

        /**
         * Build with automatic device detection and optimization
         */
        public NRVideoConfiguration buildOptimal(Context context) {
            // Auto-detect device type and apply optimal settings
            this.isTV = detectTVDevice(context);

            // Apply device-specific optimizations
            if (isTV) {
                // TV-optimized settings
                this.harvestCycleSeconds = 90;       // TV can handle longer intervals
                this.liveHarvestCycleSeconds = 45;   // Longer live intervals for TV
                this.maxBatchSizeBytes = 16384;      // 16KB batches for TV
                this.maxDeadLetterSize = 1000;       // Larger dead letter queue for TV
                this.memoryOptimized = false;        // TV has more memory available

                if (debugLoggingEnabled) {
                    System.out.println("[NRVideoConfiguration] Auto-detected Android TV - applying TV-optimized settings");
                }
            } else {
                // Mobile-optimized settings
                this.harvestCycleSeconds = 120;      // Longer intervals to save battery
                this.liveHarvestCycleSeconds = 60;   // Conservative live intervals
                this.maxBatchSizeBytes = 4096;       // Smaller 4KB batches
                this.maxDeadLetterSize = 200;        // Minimal dead letter queue
                this.memoryOptimized = true;         // Aggressive memory optimization

                if (debugLoggingEnabled) {
                    System.out.println("[NRVideoConfiguration] Auto-detected Mobile device - applying mobile-optimized settings");
                }
            }

            return new NRVideoConfiguration(this);
        }

        /**
         * Detect if this is an Android TV device
         */
        private boolean detectTVDevice(Context context) {
            try {
                PackageManager pm = context.getPackageManager();

                // Primary detection: Android TV Leanback UI
                if (pm.hasSystemFeature("android.software.leanback")) {
                    return true;
                }

                // Secondary detection: Television feature
                if (pm.hasSystemFeature("android.hardware.type.television")) {
                    return true;
                }

                // Tertiary detection: No touchscreen requirement (TV indicator)
                if (!pm.hasSystemFeature("android.hardware.touchscreen")) {
                    return true;
                }

                return false;
            } catch (Exception e) {
                return false; // Default to mobile if detection fails
            }
        }

    }

    /**
     * Minimal configuration for quick setup
     */
    public static NRVideoConfiguration minimal(String applicationToken) {
        return new Builder(applicationToken).build();
    }

    /**
     * TV-optimized configuration with larger buffers and longer intervals
     */
    public static NRVideoConfiguration forTV(String applicationToken) {
        return new Builder(applicationToken)
            .harvestCycle(90)           // TV can handle longer intervals
            .liveHarvestCycle(45)       // Longer live intervals for TV
            .maxBatchSize(16384)        // 16KB batches for TV
            .maxDeadLetterSize(1000)    // Larger dead letter queue for TV
            .memoryOptimized(false)     // TV has more memory available
            .isTV(true)
            .build();
    }

    /**
     * Mobile-optimized configuration for battery and memory efficiency
     */
    public static NRVideoConfiguration forMobile(String applicationToken) {
        return new Builder(applicationToken)
            .harvestCycle(120)          // Longer intervals to save battery
            .liveHarvestCycle(60)       // Conservative live intervals
            .maxBatchSize(4096)         // Smaller 4KB batches
            .maxDeadLetterSize(200)     // Minimal dead letter queue
            .memoryOptimized(true)      // Aggressive memory optimization
            .isTV(false)
            .build();
    }
}
