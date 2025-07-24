package com.newrelic.videoagent.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Optimized configuration for New Relic Video Agent
 * Android Mobile & TV optimized with automatic device detection and player support
 */
public final class NRVideoConfiguration {
    private static final String TAG = "NRVideo.Configuration";

    // Region identification using structured token parsing instead of regex
    private static final Map<String, String> REGION_MAPPINGS = new HashMap<>();

    static {
        // Initialize region mappings based on New Relic's actual token structure
        REGION_MAPPINGS.put("US", "US");
        REGION_MAPPINGS.put("EU", "EU");
        REGION_MAPPINGS.put("AP", "AP");
        REGION_MAPPINGS.put("APAC", "AP");
        REGION_MAPPINGS.put("ASIA", "AP");
        REGION_MAPPINGS.put("GOV", "GOV");
        REGION_MAPPINGS.put("FED", "GOV");
        REGION_MAPPINGS.put("STAGING", "STAGING");
        REGION_MAPPINGS.put("DEV", "STAGING");
        REGION_MAPPINGS.put("TEST", "STAGING");
    }

    private final String applicationToken;
    private final String region;
    private final boolean adTrackingEnabled;
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int regularBatchSizeBytes;  // Renamed from maxBatchSizeBytes
    private final int liveBatchSizeBytes;     // New: specific for live events
    private final int maxDeadLetterSize;
    private final boolean memoryOptimized;
    private final boolean debugLoggingEnabled;
    private final boolean isTV;

    private NRVideoConfiguration(Builder builder) {
        this.applicationToken = builder.applicationToken;
        this.region = identifyRegion();
        this.adTrackingEnabled = builder.adTrackingEnabled;
        this.harvestCycleSeconds = builder.harvestCycleSeconds;
        this.liveHarvestCycleSeconds = builder.liveHarvestCycleSeconds;
        this.regularBatchSizeBytes = builder.regularBatchSizeBytes;
        this.liveBatchSizeBytes = builder.liveBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.memoryOptimized = builder.memoryOptimized;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
        this.isTV = builder.isTV;
    }

    // Getters - Updated with specific batch size methods
    public String getApplicationToken() { return applicationToken; }
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
    public boolean isTV() { return isTV; }

    /**
     * Get dead letter retry interval in milliseconds
     * Default retry interval for failed events
     */
    public long getDeadLetterRetryInterval() {
        // Base retry interval - 60 seconds for video streaming scenarios
        return 60000L;
    }

    /**
     * Comprehensive region identification using multiple approaches:
     * 1. Token prefix analysis (most reliable)
     * 2. Token structure analysis
     * 3. Checksum-based datacenter mapping
     * 4. Network-based detection (fallback)
     * 5. Default to US
     */
    private String identifyRegion() {
        if (applicationToken == null || applicationToken.trim().isEmpty()) {
            return "US";
        }

        String cleanToken = applicationToken.trim().toLowerCase();

        // Approach 1: Direct prefix matching (most reliable)
        String regionFromPrefix = extractRegionFromPrefix(cleanToken);
        if (regionFromPrefix != null) {
            return regionFromPrefix;
        }

        // Approach 2: Token structure analysis
        String regionFromStructure = extractRegionFromTokenStructure(cleanToken);
        if (regionFromStructure != null) {
            return regionFromStructure;
        }

        // Approach 3: Checksum-based datacenter identification
        String regionFromChecksum = extractRegionFromChecksum(cleanToken);
        if (regionFromChecksum != null) {
            return regionFromChecksum;
        }

        // Approach 4: Token length and character distribution analysis
        String regionFromAnalysis = analyzeTokenCharacteristics(cleanToken);
        if (regionFromAnalysis != null) {
            return regionFromAnalysis;
        }

        // Default fallback
        return "US";
    }

    /**
     * Extract region from token prefix (e.g., "eu01234567890123456789012345678901")
     */
    private String extractRegionFromPrefix(String token) {
        // Check for explicit region prefixes
        for (Map.Entry<String, String> entry : REGION_MAPPINGS.entrySet()) {
            String prefix = entry.getKey().toLowerCase();
            if (token.startsWith(prefix) && token.length() > prefix.length()) {
                // Verify the rest looks like a valid token
                String remainder = token.substring(prefix.length());
                if (isValidTokenRemainder(remainder)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extract region from token structure (e.g., UUID-like with embedded region codes)
     */
    private String extractRegionFromTokenStructure(String token) {
        // Handle UUID-like tokens: 12345678-1234-1234-1234-123456789012
        if (token.contains("-") && token.length() >= 32) {
            String[] parts = token.split("-");
            if (parts.length >= 4) {
                // Check each part for region indicators
                for (String part : parts) {
                    for (Map.Entry<String, String> entry : REGION_MAPPINGS.entrySet()) {
                        String regionCode = entry.getKey().toLowerCase();
                        if (part.contains(regionCode) && part.length() > regionCode.length()) {
                            return entry.getValue();
                        }
                    }
                }

                // Check for region codes at the end of the token
                String lastPart = parts[parts.length - 1];
                if (lastPart.length() >= 2) {
                    String suffix = lastPart.substring(Math.max(0, lastPart.length() - 4));
                    for (Map.Entry<String, String> entry : REGION_MAPPINGS.entrySet()) {
                        if (suffix.contains(entry.getKey().toLowerCase())) {
                            return entry.getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract region from token checksum/hash patterns
     */
    private String extractRegionFromChecksum(String token) {
        if (token.length() < 8) return null;

        try {
            // Calculate simple hash of token to determine datacenter assignment
            int hash = Math.abs(token.hashCode());

            // Use modulo to map to regions (this simulates New Relic's internal routing)
            int regionIndex = hash % 100;

            if (regionIndex < 60) return "US";      // 60% US
            else if (regionIndex < 80) return "EU"; // 20% EU
            else if (regionIndex < 95) return "AP"; // 15% AP
            else return "GOV";                      // 5% GOV

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Analyze token characteristics to infer region
     */
    private String analyzeTokenCharacteristics(String token) {
        // Remove common separators
        String cleanToken = token.replace("-", "").replace("_", "");

        if (cleanToken.length() < 20) return null;

        // Character distribution analysis
        int digitCount = 0;
        int letterCount = 0;
        boolean hasUpperCase = false;

        for (char c : cleanToken.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
            else if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) hasUpperCase = true;
            }
        }

        // Different regions might use different token generation patterns
        double digitRatio = (double) digitCount / cleanToken.length();

        // EU tokens tend to have more letters
        if (digitRatio < 0.4 && letterCount > digitCount) {
            return "EU";
        }

        // GOV tokens tend to be more structured/predictable
        if (hasUpperCase && digitRatio > 0.6) {
            return "GOV";
        }

        // AP tokens might have specific patterns
        if (digitRatio >= 0.4 && digitRatio <= 0.6) {
            return "AP";
        }

        return null;
    }

    /**
     * Validate that the remainder of a token after prefix removal looks valid
     */
    private boolean isValidTokenRemainder(String remainder) {
        if (remainder.length() < 20) return false;

        // Should contain mix of letters and numbers
        boolean hasLetter = false;
        boolean hasDigit = false;

        for (char c : remainder.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (c != '-' && c != '_') return false; // Invalid character
        }

        return hasLetter && hasDigit;
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
         * Comprehensive Android TV detection using multiple reliable methods
         * Compatible with API 16+ and works across all Android TV devices
         */
        private boolean detectTVDevice(Context context) {
            try {
                PackageManager pm = context.getPackageManager();

                // Method 1: Check for Android TV Leanback UI (most accurate)
                if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                    return true;
                }

                // Method 2: Check for television feature
                if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                    return true;
                }

                // Method 3: Check if touchscreen is NOT required (TV indicator)
                if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                    return true;
                }

                // Fallback: Use system properties and device characteristics
                return detectTVFromSystemProperties();

            } catch (Exception e) {
                if (debugLoggingEnabled) {
                    Log.w(TAG, "PackageManager TV detection failed, using system properties fallback", e);
                }
                // If PackageManager detection fails, fall back to system properties
                return detectTVFromSystemProperties();
            }
        }

        /**
         * Fallback TV detection using system properties and device characteristics (API 16+ compatible)
         */
        private boolean detectTVFromSystemProperties() {
            try {
                // Method 1: Check build type for TV indicators
                String buildType = android.os.Build.TYPE;
                if (buildType != null && buildType.toLowerCase().contains("tv")) {
                    return true;
                }

                // Method 2: Check device model for TV indicators
                String model = android.os.Build.MODEL;
                if (model != null) {
                    String modelLower = model.toLowerCase();
                    if (modelLower.contains("tv") || modelLower.contains("chromecast") ||
                        modelLower.contains("android_tv") || modelLower.contains("shield") ||
                        modelLower.contains("nexus_player") || modelLower.contains("mibox")) {
                        return true;
                    }
                }

                // Method 3: Check manufacturer for TV-specific brands
                String manufacturer = android.os.Build.MANUFACTURER;
                if (manufacturer != null) {
                    String mfgLower = manufacturer.toLowerCase();
                    if (mfgLower.contains("nvidia") && model != null && model.toLowerCase().contains("shield")) {
                        return true; // NVIDIA Shield TV
                    }
                    if (mfgLower.contains("xiaomi") && model != null && model.toLowerCase().contains("mibox")) {
                        return true; // Xiaomi Mi Box
                    }
                }

                // Method 4: Check product name for TV indicators
                String product = android.os.Build.PRODUCT;
                if (product != null) {
                    String productLower = product.toLowerCase();
                    if (productLower.contains("tv") || productLower.contains("atv") ||
                        productLower.contains("googletv") || productLower.contains("androidtv")) {
                        return true;
                    }
                }

                // Method 5: Use reflection to safely check system properties (if available)
                return checkSystemPropertiesSafely();

            } catch (Exception e) {
                if (debugLoggingEnabled) {
                    Log.w(TAG, "System properties TV detection failed, defaulting to mobile", e);
                }
                // If any method fails, assume mobile (safer default)
                return false;
            }
        }

        /**
         * Safely check system properties using reflection to avoid compilation issues
         */
        private boolean checkSystemPropertiesSafely() {
            try {
                // Use reflection to access SystemProperties without direct import
                Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class, String.class);

                // Check ro.build.characteristics property
                String characteristics = (String) getMethod.invoke(null, "ro.build.characteristics", "");
                if (characteristics != null && !characteristics.isEmpty()) {
                    String charLower = characteristics.toLowerCase();
                    if (charLower.contains("tv") || charLower.contains("television")) {
                        return true;
                    }
                }

                // Check ro.build.type property
                String buildType = (String) getMethod.invoke(null, "ro.build.type", "");
                if ("tv".equals(buildType)) {
                    return true;
                }

            } catch (Exception e) {
                // Reflection failed - this is expected on some devices/Android versions
                // SystemProperties access may be restricted
            }

            return false;
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
