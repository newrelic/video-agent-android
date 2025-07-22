package com.newrelic.videoagent.core;

import android.content.Context;
import java.util.Locale;

/**
 * Optimized configuration for New Relic Video Agent
 * Android Mobile & TV optimized with automatic device detection and player support
 */
public final class NRVideoConfiguration {

    // Core settings
    private final String applicationToken;
    private final String endpointUrl;
    private final String region;

    // Video tracking settings
    private final boolean adTrackingEnabled;
    private final boolean autoStartTracking;
    private final PlayerType playerType;

    // Harvest settings (size-optimized for mobile/TV)
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int maxBatchSizeBytes;
    private final int maxDeadLetterSize;
    private final boolean memoryOptimized;
    private final boolean debugLoggingEnabled;

    /**
     * Supported player types
     */
    public enum PlayerType {
        EXOPLAYER,  // ExoPlayer for video content
        IMA         // IMA for ads (can be combined with ExoPlayer)
    }

    private NRVideoConfiguration(Builder builder) {
        this.applicationToken = builder.applicationToken;
        this.endpointUrl = builder.endpointUrl;
        this.region = builder.region;
        this.adTrackingEnabled = builder.adTrackingEnabled;
        this.autoStartTracking = builder.autoStartTracking;
        this.playerType = builder.playerType;
        this.harvestCycleSeconds = builder.harvestCycleSeconds;
        this.liveHarvestCycleSeconds = builder.liveHarvestCycleSeconds;
        this.maxBatchSizeBytes = builder.maxBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.memoryOptimized = builder.memoryOptimized;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
    }

    // Getters
    public String getApplicationToken() { return applicationToken; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getRegion() { return region; }
    public boolean isAdTrackingEnabled() { return adTrackingEnabled; }
    public boolean isAutoStartTracking() { return autoStartTracking; }
    public PlayerType getPlayerType() { return playerType; }
    public int getHarvestCycleSeconds() { return harvestCycleSeconds; }
    public int getLiveHarvestCycleSeconds() { return liveHarvestCycleSeconds; }
    public int getMaxBatchSizeBytes() { return maxBatchSizeBytes; }
    public int getMaxDeadLetterSize() { return maxDeadLetterSize; }
    public boolean isMemoryOptimized() { return memoryOptimized; }
    public boolean isDebugLoggingEnabled() { return debugLoggingEnabled; }

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

        // Default settings (will be overridden by auto-detection)
        private String endpointUrl;
        private String region = "US";
        private boolean adTrackingEnabled = false;  // Disabled by default, user must explicitly enable
        private boolean autoStartTracking = true;
        private PlayerType playerType = PlayerType.EXOPLAYER;  // Default to ExoPlayer
        private int harvestCycleSeconds = 60;
        private int liveHarvestCycleSeconds = 30;
        private int maxBatchSizeBytes = 8192;
        private int maxDeadLetterSize = 500;
        private boolean memoryOptimized = true;
        private boolean debugLoggingEnabled = false;

        public Builder(String applicationToken) {
            if (applicationToken == null || applicationToken.trim().isEmpty()) {
                throw new IllegalArgumentException("Application token is required");
            }
            this.applicationToken = applicationToken;
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

        public Builder playerType(PlayerType playerType) {
            this.playerType = playerType;
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
            if (seconds < 5) throw new IllegalArgumentException("Live harvest cycle must be at least 5 seconds");
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

        public Builder enableDebugLogging(boolean enabled) {
            this.debugLoggingEnabled = enabled;
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
            boolean isTV = detectTVDevice(context);

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

            // Auto-detect region if not set
            if ("US".equals(region)) {
                region = detectRegion();
                endpointUrl = generateEndpointUrl(region);
            }

            return new NRVideoConfiguration(this);
        }

        private String generateEndpointUrl(String region) {
            String domain = "EU".equalsIgnoreCase(region) ?
                "mobile-collector.eu.newrelic.com" :
                "mobile-collector.newrelic.com";
            return "https://" + domain + "/mobile/v1/events";
        }

        /**
         * Detect if this is an Android TV device
         */
        private boolean detectTVDevice(Context context) {
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();

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

        private String detectRegion() {
            String country = Locale.getDefault().getCountry();
            if (country.equalsIgnoreCase("US")) return "US";

            // EU countries
            String[] euCountries = {"GB", "FR", "DE", "IT", "ES", "NL", "BE", "SE",
                                   "DK", "FI", "NO", "IE", "PT", "GR", "AT", "CH"};

            for (String euCountry : euCountries) {
                if (country.equalsIgnoreCase(euCountry)) {
                    return "EU";
                }
            }

            return "US"; // Default fallback
        }
    }

    /**
     * Minimal configuration for quick setup
     */
    public static NRVideoConfiguration minimal(String applicationToken) {
        return new Builder(applicationToken).build();
    }

    /**
     * ExoPlayer configuration (video content only)
     */
    public static NRVideoConfiguration forExoPlayer(String applicationToken) {
        return new Builder(applicationToken)
            .playerType(PlayerType.EXOPLAYER)
            .enableAdTracking(false)
            .build();
    }

    /**
     * ExoPlayer with IMA ads configuration
     */
    public static NRVideoConfiguration forExoPlayerWithAds(String applicationToken) {
        return new Builder(applicationToken)
            .playerType(PlayerType.EXOPLAYER)
            .enableAdTracking(true)
            .build();
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
            .build();
    }
}
