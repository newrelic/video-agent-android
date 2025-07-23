package com.newrelic.videoagent.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Optimized configuration for New Relic Video Agent
 * Android Mobile & TV optimized with automatic device detection and player support
 */
public final class NRVideoConfiguration {
    private static final String TAG = "NRVideo.Configuration";

    private final String applicationToken;
    private final String endpointUrl;
    private final String region;
    private final boolean adTrackingEnabled;
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int regularBatchSizeBytes;  // Renamed from maxBatchSizeBytes
    private final int liveBatchSizeBytes;     // New: specific for live events
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
        this.regularBatchSizeBytes = builder.regularBatchSizeBytes;
        this.liveBatchSizeBytes = builder.liveBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.memoryOptimized = builder.memoryOptimized;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
        this.enableCrashSafety = builder.enableCrashSafety;
        this.isTV = builder.isTV;
    }

    // Getters - Updated with specific batch size methods
    public String getApplicationToken() { return applicationToken; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getRegion() { return region; }
    public boolean isAdTrackingEnabled() { return adTrackingEnabled; }
    public int getHarvestCycleSeconds() { return harvestCycleSeconds; }
    public int getLiveHarvestCycleSeconds() { return liveHarvestCycleSeconds; }

    // New specific batch size methods - no more fallback/default
    public int getRegularBatchSizeBytes() { return regularBatchSizeBytes; }
    public int getLiveBatchSizeBytes() { return liveBatchSizeBytes; }

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
        private int regularBatchSizeBytes = 4096;  // Renamed from maxBatchSizeBytes
        private int liveBatchSizeBytes = 4096;     // New: specific for live events
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

        public Builder regularBatchSize(int bytes) {
            if (bytes < 1024) throw new IllegalArgumentException("Regular batch size must be at least 1KB");
            this.regularBatchSizeBytes = bytes;
            return this;
        }

        public Builder liveBatchSize(int bytes) {
            if (bytes < 1024) throw new IllegalArgumentException("Live batch size must be at least 1KB");
            this.liveBatchSizeBytes = bytes;
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
                this.regularBatchSizeBytes = 16384;  // 16KB batches for TV
                this.liveBatchSizeBytes = 16384;     // 16KB live batches for TV
                this.maxDeadLetterSize = 1000;       // Larger dead letter queue for TV
                this.memoryOptimized = false;        // TV has more memory available

                if (debugLoggingEnabled) {
                    Log.d(TAG, "Auto-detected Android TV - applying TV-optimized settings " +
                        "(harvest: " + harvestCycleSeconds + "s, live: " + liveHarvestCycleSeconds + "s, " +
                        "regular batch: " + regularBatchSizeBytes + " bytes, live batch: " + liveBatchSizeBytes + " bytes)");
                }
            } else {
                // Mobile-optimized settings
                this.harvestCycleSeconds = 120;      // Longer intervals to save battery
                this.liveHarvestCycleSeconds = 60;   // Conservative live intervals
                this.regularBatchSizeBytes = 4096;   // Smaller 4KB batches
                this.liveBatchSizeBytes = 4096;      // 4KB live batches
                this.maxDeadLetterSize = 200;        // Minimal dead letter queue
                this.memoryOptimized = true;         // Aggressive memory optimization

                if (debugLoggingEnabled) {
                    Log.d(TAG, "Auto-detected Mobile device - applying mobile-optimized settings " +
                        "(harvest: " + harvestCycleSeconds + "s, live: " + liveHarvestCycleSeconds + "s, " +
                        "regular batch: " + regularBatchSizeBytes + " bytes, live batch: " + liveBatchSizeBytes + " bytes)");
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
                if (debugLoggingEnabled) {
                    Log.w(TAG, "TV detection failed, defaulting to mobile", e);
                }
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
     * TV-optimized configuration with device-specific optimizations
     * Considers: larger memory, stable network assumptions, longer sessions, better CPU
     * NO PERMISSIONS REQUIRED - privacy-friendly approach
     */
    public static NRVideoConfiguration forTV(String applicationToken, Context context) {
        Builder builder = new Builder(applicationToken)
            .isTV(true)
            .memoryOptimized(false);    // TV has more memory available

        // Detect TV capabilities and optimize accordingly
        try {
            // Get available memory (no permissions required)
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);

            long availableMemoryMB = memInfo.availMem / (1024 * 1024);

            if (availableMemoryMB > 2048) { // > 2GB available
                // High-end TV: aggressive batching (assume good network)
                builder.harvestCycle(75)           // Shorter cycles for better responsiveness
                       .liveHarvestCycle(30)       // Very fast live processing
                       .regularBatchSize(131072)   // 128KB batches (64 events × 2KB)
                       .liveBatchSize(65536)       // 64KB live batches (32 events × 2KB)
                       .maxDeadLetterSize(2000);   // Large retry queue
            } else if (availableMemoryMB > 1024) { // 1-2GB available
                // Mid-range TV: balanced approach (assume decent network)
                builder.harvestCycle(90)           // Standard TV intervals
                       .liveHarvestCycle(45)       // Balanced live processing
                       .regularBatchSize(102400)   // 100KB batches (50 events × 2KB)
                       .liveBatchSize(51200)       // 50KB live batches (25 events × 2KB)
                       .maxDeadLetterSize(1500);   // Medium retry queue
            } else {
                // Lower-end TV: conservative approach (assume limited network)
                builder.harvestCycle(120)          // Longer intervals to reduce load
                       .liveHarvestCycle(60)       // Conservative live processing
                       .regularBatchSize(61440)    // 60KB batches (30 events × 2KB)
                       .liveBatchSize(30720)       // 30KB live batches (15 events × 2KB)
                       .maxDeadLetterSize(1000);   // Standard retry queue
            }

            // TV devices typically have stable WiFi connections, so we can be more aggressive
            // Apply WiFi-optimized settings without requiring network permissions
            int currentRegularBatch = builder.regularBatchSizeBytes;
            int currentLiveBatch = builder.liveBatchSizeBytes;

            builder.regularBatchSize((int)(currentRegularBatch * 1.3))  // 30% larger batches (assume WiFi)
                   .liveBatchSize((int)(currentLiveBatch * 1.2));       // 20% larger live batches

        } catch (Exception e) {
            // Fallback to standard TV configuration if detection fails
            builder.harvestCycle(90)
                   .liveHarvestCycle(45)
                   .regularBatchSize(102400)   // 100KB batches
                   .liveBatchSize(51200)       // 50KB live batches
                   .maxDeadLetterSize(1000);
        }

        return builder.build();
    }

    /**
     * Mobile-optimized configuration with device-specific optimizations
     * Considers: battery life, conservative data usage, memory pressure
     * NO PERMISSIONS REQUIRED - privacy-friendly approach
     */
    public static NRVideoConfiguration forMobile(String applicationToken, Context context) {
        Builder builder = new Builder(applicationToken)
            .isTV(false)
            .memoryOptimized(true);     // Aggressive memory optimization

        try {
            // Detect device capabilities (no permissions required)
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);

            long availableMemoryMB = memInfo.availMem / (1024 * 1024);
            boolean isLowMemory = memInfo.lowMemory;

            // Memory-based optimization (conservative for mobile networks)
            if (isLowMemory || availableMemoryMB < 512) {
                // Low memory device: very conservative (assume cellular data concerns)
                builder.harvestCycle(200)          // 3.3 min cycles to reduce memory pressure and data usage
                       .liveHarvestCycle(100)      // 1.7 min live cycles
                       .regularBatchSize(16384)    // 16KB batches (8 events × 2KB) - small for data savings
                       .liveBatchSize(8192)        // 8KB live batches (4 events × 2KB)
                       .maxDeadLetterSize(50);     // Minimal retry queue
            } else if (availableMemoryMB < 1024) {
                // Mid-range device: balanced mobile optimization (assume mixed network usage)
                builder.harvestCycle(180)          // 3 min cycles
                       .liveHarvestCycle(90)       // 1.5 min live cycles
                       .regularBatchSize(32768)    // 32KB batches (16 events × 2KB)
                       .liveBatchSize(16384)       // 16KB live batches (8 events × 2KB)
                       .maxDeadLetterSize(150);    // Small retry queue
            } else {
                // High-end mobile: optimized but still conservative for data usage
                builder.harvestCycle(150)          // 2.5 min cycles
                       .liveHarvestCycle(75)       // 1.25 min live cycles
                       .regularBatchSize(49152)    // 48KB batches (24 events × 2KB)
                       .liveBatchSize(24576)       // 24KB live batches (12 events × 2KB)
                       .maxDeadLetterSize(250);    // Medium retry queue
            }

            // Battery optimization (API 16+ compatible approach)
            try {
                // Use older API for battery level that works on API 16+
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, ifilter);

                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    if (level >= 0 && scale > 0) {
                        int batteryLevel = (int) ((level / (float) scale) * 100);

                        if (batteryLevel < 20) {
                            // Low battery: very conservative
                            builder.harvestCycle(builder.harvestCycleSeconds + 90)       // Add 1.5 min to cycles
                                   .liveHarvestCycle(builder.liveHarvestCycleSeconds + 45); // Add 45s to live cycles
                        } else if (batteryLevel < 50) {
                            // Medium battery: slightly conservative
                            builder.harvestCycle(builder.harvestCycleSeconds + 30)       // Add 30s to cycles
                                   .liveHarvestCycle(builder.liveHarvestCycleSeconds + 15); // Add 15s to live cycles
                        }
                        // High battery (>50%): use standard mobile settings
                    }
                }
            } catch (Exception batteryException) {
                // Battery detection failed, use default conservative settings
                // No action needed, already conservative
            }

        } catch (Exception e) {
            // Fallback to conservative mobile configuration if detection fails
            builder.harvestCycle(180)
                   .liveHarvestCycle(90)
                   .regularBatchSize(32768)    // 32KB batches
                   .liveBatchSize(16384)       // 16KB live batches
                   .maxDeadLetterSize(150);
        }

        return builder.build();
    }
}
