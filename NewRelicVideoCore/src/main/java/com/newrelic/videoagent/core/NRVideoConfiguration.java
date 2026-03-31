package com.newrelic.videoagent.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import com.newrelic.videoagent.core.utils.NRLog;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe, immutable configuration with Android Mobile & TV optimizations
 */
public final class NRVideoConfiguration {

    // Thread-safe region mappings - immutable after initialization
    private static final Map<String, String> REGION_MAPPINGS;

    static {
        Map<String, String> regions = new HashMap<>();
        regions.put("US", "US");
        regions.put("EU", "EU");
        regions.put("AP", "AP");
        regions.put("APAC", "AP");
        regions.put("ASIA", "AP");
        regions.put("GOV", "GOV");
        regions.put("FED", "GOV");
        regions.put("STAGING", "STAGING");
        regions.put("DEV", "STAGING");
        regions.put("TEST", "STAGING");
        REGION_MAPPINGS = regions; // Immutable reference
    }

    // Immutable configuration fields
    private final String applicationToken;
    private final String region;
    private final int harvestCycleSeconds;
    private final int liveHarvestCycleSeconds;
    private final int regularBatchSizeBytes;
    private final int liveBatchSizeBytes;
    private final int maxDeadLetterSize;
    private final boolean memoryOptimized;
    private final boolean debugLoggingEnabled;
    private final boolean isTV;
    private final String collectorAddress;

    // Runtime configuration fields (mutable, thread-safe) - Using AtomicBoolean for better performance
    private final AtomicBoolean qoeAggregateEnabled = new AtomicBoolean(true);
    private final AtomicBoolean runtimeConfigInitialized = new AtomicBoolean(false);


    // Performance optimization constants
    private static final int DEFAULT_HARVEST_CYCLE_SECONDS = 5 * 60; // 5 minutes
    private static final int DEFAULT_LIVE_HARVEST_CYCLE_SECONDS = 30; // 30 seconds
    private static final int DEFAULT_REGULAR_BATCH_SIZE_BYTES = 64 * 1024; // 64KB
    private static final int DEFAULT_LIVE_BATCH_SIZE_BYTES = 32 * 1024;    // 32KB
    private static final int DEFAULT_MAX_DEAD_LETTER_SIZE = 100;

    // TV-specific optimizations
    private static final int TV_HARVEST_CYCLE_SECONDS = 3 * 60; // 3 minutes
    private static final int TV_LIVE_HARVEST_CYCLE_SECONDS = 10; // 10 seconds
    private static final int TV_REGULAR_BATCH_SIZE_BYTES = 128 * 1024; // 128KB
    private static final int TV_LIVE_BATCH_SIZE_BYTES = 64 * 1024;     // 64KB

    // Memory-optimized settings
    private static final int MEMORY_OPTIMIZED_HARVEST_CYCLE_SECONDS = 60;
    private static final int MEMORY_OPTIMIZED_LIVE_HARVEST_CYCLE_SECONDS = 15;
    private static final int MEMORY_OPTIMIZED_REGULAR_BATCH_SIZE_BYTES = 32 * 1024; // 32KB
    private static final int MEMORY_OPTIMIZED_LIVE_BATCH_SIZE_BYTES = 16 * 1024;    // 16KB
    private static final int MEMORY_OPTIMIZED_MAX_DEAD_LETTER_SIZE = 50;

    private NRVideoConfiguration(Builder builder) {
        if (builder.applicationToken == null || builder.applicationToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Application token cannot be null or empty");
        }

        this.applicationToken = builder.applicationToken.trim();
        this.region = identifyRegion(this.applicationToken);
        this.harvestCycleSeconds = builder.harvestCycleSeconds;
        this.liveHarvestCycleSeconds = builder.liveHarvestCycleSeconds;
        this.regularBatchSizeBytes = builder.regularBatchSizeBytes;
        this.liveBatchSizeBytes = builder.liveBatchSizeBytes;
        this.maxDeadLetterSize = builder.maxDeadLetterSize;
        this.memoryOptimized = builder.memoryOptimized;
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
        this.isTV = builder.isTV;
        this.collectorAddress = builder.collectorAddress;

        // Initialize runtime configuration
        this.qoeAggregateEnabled.set(builder.qoeAggregateEnabled);
        this.runtimeConfigInitialized.set(true);
    }

    // Immutable getters
    public String getApplicationToken() { return applicationToken; }
    public String getRegion() { return region; }
    public int getHarvestCycleSeconds() { return harvestCycleSeconds; }
    public int getLiveHarvestCycleSeconds() { return liveHarvestCycleSeconds; }
    public int getRegularBatchSizeBytes() { return regularBatchSizeBytes; }
    public int getLiveBatchSizeBytes() { return liveBatchSizeBytes; }
    public int getMaxDeadLetterSize() { return maxDeadLetterSize; }
    public boolean isMemoryOptimized() { return memoryOptimized; }
    public boolean isDebugLoggingEnabled() { return debugLoggingEnabled; }
    public boolean isTV() { return isTV; }
    public String getCollectorAddress() { return collectorAddress; }

    // Runtime configuration getters and setters
    /**
     * Check if QOE_AGGREGATE events should be sent during harvest cycles
     * @return true if QOE_AGGREGATE should be sent, false otherwise
     */
    public boolean isQoeAggregateEnabled() {
        if (!runtimeConfigInitialized.get()) {
            throw new IllegalStateException("NRVideoConfiguration not initialized! Call build() first.");
        }
        return qoeAggregateEnabled.get();
    }

    /**
     * Set whether QOE_AGGREGATE events should be sent during harvest cycles
     * Lock-free, thread-safe runtime configuration using AtomicBoolean
     * @param enabled true to enable QOE_AGGREGATE, false to disable
     */
    public void setQoeAggregateEnabled(boolean enabled) {
        this.qoeAggregateEnabled.set(enabled);
    }

    /**
     * Initialize configuration with client settings
     * @param clientQoeAggregateEnabled QOE aggregate setting from client (null if not provided)
     */
    public void initializeFromClient(Boolean clientQoeAggregateEnabled) {
        // If client provides a value, use it; otherwise keep current default
        if (clientQoeAggregateEnabled != null) {
            this.qoeAggregateEnabled.set(clientQoeAggregateEnabled);
        }
    }

    /**
     * Get dead letter retry interval in milliseconds
     * Optimized for different device types and network conditions
     */
    public long getDeadLetterRetryInterval() {
        if (isTV) {
            return 120000L; // 2 minutes for TV (more stable network)
        } else if (memoryOptimized) {
            return 180000L; // 3 minutes for low-memory devices
        } else {
            return 60000L;  // 1 minute for standard mobile
        }
    }

    /**
     * Parse region code from application token prefix
     * Matches NewRelic iOS Agent pattern: extracts region prefix before 'x'
     * Examples: "EUxABCD..." -> "EU", "APxABCD..." -> "AP", "AA..." -> ""
     */
    private static String parseRegionFromToken(String applicationToken) {
        if (applicationToken == null || applicationToken.length() < 3) {
            return "";
        }

        // Find the first 'x' in the token
        int xIndex = applicationToken.indexOf('x');
        if (xIndex == -1) {
            return ""; // No region prefix found
        }

        // Extract everything before the first 'x'
        String regionCode = applicationToken.substring(0, xIndex);

        // Remove any trailing 'x' characters
        while (regionCode.length() > 0 && regionCode.charAt(regionCode.length() - 1) == 'x') {
            regionCode = regionCode.substring(0, regionCode.length() - 1);
        }

        return regionCode;
    }

    /**
     * Identify region with proper token parsing and fallback logic
     * Behavior similar to NewRelic iOS Agent's NRMAAgentConfiguration
     */
    private static String identifyRegion(String applicationToken) {
        if (applicationToken == null || applicationToken.length() < 10) {
            return "US"; // Safe default
        }

        String cleanToken = applicationToken.trim().toLowerCase();

        // Strategy 1: Direct prefix matching (most reliable)
        for (Map.Entry<String, String> entry : REGION_MAPPINGS.entrySet()) {
            String regionKey = entry.getKey().toLowerCase();
            if (cleanToken.startsWith(regionKey) || cleanToken.contains("-" + regionKey + "-")) {
                return entry.getValue();
            }
        }

        // Strategy 2: Token structure analysis
        if (cleanToken.length() >= 40) { // Standard NR token length
            // EU tokens often have specific patterns
            if (cleanToken.contains("eu") || cleanToken.contains("europe")) {
                return "EU";
            }
            // AP tokens often have specific patterns
            if (cleanToken.contains("ap") || cleanToken.contains("asia") || cleanToken.contains("pacific")) {
                return "AP";
            }
            // Gov tokens have specific patterns
            if (cleanToken.contains("gov") || cleanToken.contains("fed")) {
                return "GOV";
            }
        }

        // Strategy 3: Default to US for production stability
        return "US";
    }

    /**
     * Builder pattern for thread-safe configuration creation
     */
    public static final class Builder {
        private String applicationToken;
        private int harvestCycleSeconds = DEFAULT_HARVEST_CYCLE_SECONDS;
        private int liveHarvestCycleSeconds = DEFAULT_LIVE_HARVEST_CYCLE_SECONDS;
        private int regularBatchSizeBytes = DEFAULT_REGULAR_BATCH_SIZE_BYTES;
        private int liveBatchSizeBytes = DEFAULT_LIVE_BATCH_SIZE_BYTES;
        private int maxDeadLetterSize = DEFAULT_MAX_DEAD_LETTER_SIZE;
        private boolean memoryOptimized = true;
        private boolean debugLoggingEnabled = false;
        private boolean isTV = false;
        private String collectorAddress = null;
        private boolean qoeAggregateEnabled = true; // Default enabled

        public Builder(String applicationToken) {
            this.applicationToken = applicationToken;
        }

        /**
         * Auto-detect TV platform and apply optimizations
         */
        public Builder autoDetectPlatform(Context context) {
            if (context != null) {
                this.isTV = detectTVPlatform(context);
                if (this.isTV) {
                    applyTVOptimizations();
                }

                // Auto-detect low memory conditions
                if (detectLowMemoryDevice(context)) {
                    this.memoryOptimized = true;
                    applyMemoryOptimizations();
                }
            }
            return this;
        }

        public Builder withHarvestCycle(int seconds) {
            if (seconds < 5 || seconds > 300) {
                throw new IllegalArgumentException("Harvest cycle must be between 5-300 seconds");
            }
            this.harvestCycleSeconds = seconds;
            return this;
        }

        public Builder withLiveHarvestCycle(int seconds) {
            if (seconds < 1 || seconds > 60) {
                throw new IllegalArgumentException("Live harvest cycle must be between 1-60 seconds");
            }
            this.liveHarvestCycleSeconds = seconds;
            return this;
        }

        public Builder withRegularBatchSize(int bytes) {
            if (bytes < 1024 || bytes > 1024 * 1024) { // 1KB to 1MB
                throw new IllegalArgumentException("Regular batch size must be between 1KB-1MB");
            }
            this.regularBatchSizeBytes = bytes;
            return this;
        }

        public Builder withLiveBatchSize(int bytes) {
            if (bytes < 512 || bytes > 512 * 1024) { // 512B to 512KB
                throw new IllegalArgumentException("Live batch size must be between 512B-512KB");
            }
            this.liveBatchSizeBytes = bytes;
            return this;
        }

        public Builder withMaxDeadLetterSize(int size) {
            if (size < 10 || size > 1000) {
                throw new IllegalArgumentException("Max dead letter size must be between 10-1000");
            }
            this.maxDeadLetterSize = size;
            return this;
        }

        public Builder withMemoryOptimization(boolean enabled) {
            this.memoryOptimized = enabled;
            if (enabled) {
                applyMemoryOptimizations();
            }
            return this;
        }

        public Builder enableLogging() {
            this.debugLoggingEnabled = true;
            return this;
        }

        /**
         * Set custom collector domain address for /connect and /data endpoints (optional)
         * Example: "staging-mobile-collector.newrelic.com" or "mobile-collector.newrelic.com"
         * If not set, will be auto-detected from application token region
         */
        public Builder withCollectorAddress(String collectorAddress) {
            this.collectorAddress = collectorAddress;
            return this;
        }

        /**
         * Enable QOE aggregate events (default: enabled)
         * @return Builder instance for method chaining
         */
        public Builder enableQoeAggregate() {
            this.qoeAggregateEnabled = true;
            return this;
        }

        /**
         * Configure QOE aggregate events
         * @param enabled true to enable QOE_AGGREGATE events, false to disable
         * @return Builder instance for method chaining
         */
        public Builder enableQoeAggregate(boolean enabled) {
            this.qoeAggregateEnabled = enabled;
            return this;
        }

        private void applyTVOptimizations() {
            this.harvestCycleSeconds = TV_HARVEST_CYCLE_SECONDS;
            this.liveHarvestCycleSeconds = TV_LIVE_HARVEST_CYCLE_SECONDS;
            this.regularBatchSizeBytes = TV_REGULAR_BATCH_SIZE_BYTES;
            this.liveBatchSizeBytes = TV_LIVE_BATCH_SIZE_BYTES;
        }

        private void applyMemoryOptimizations() {
            this.harvestCycleSeconds = MEMORY_OPTIMIZED_HARVEST_CYCLE_SECONDS;
            this.liveHarvestCycleSeconds = MEMORY_OPTIMIZED_LIVE_HARVEST_CYCLE_SECONDS;
            this.regularBatchSizeBytes = MEMORY_OPTIMIZED_REGULAR_BATCH_SIZE_BYTES;
            this.liveBatchSizeBytes = MEMORY_OPTIMIZED_LIVE_BATCH_SIZE_BYTES;
            this.maxDeadLetterSize = MEMORY_OPTIMIZED_MAX_DEAD_LETTER_SIZE;
        }

        public NRVideoConfiguration build() {
            NRVideoConfiguration config = new NRVideoConfiguration(this);
            // Mark runtime configuration as initialized
            config.runtimeConfigInitialized.set(true);
            return config;
        }
    }

    /**
     * Detect Android TV platform with multiple strategies
     * Thread-safe and optimized for performance
     */
    private static boolean detectTVPlatform(Context context) {
        try {
            PackageManager pm = context.getPackageManager();

            // Primary detection: TV feature
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                return true;
            }

            // Secondary detection: Touch screen absence
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true;
            }

            // Tertiary detection: UI mode (API 8+)
            android.content.res.Configuration config = context.getResources().getConfiguration();
            return (config.uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                   == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;

        } catch (Exception e) {
            NRLog.w("Failed to detect TV platform: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detect low memory device conditions
     * Optimized for performance and battery life
     */
    private static boolean detectLowMemoryDevice(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return false;
            }

            // Check if device is low RAM
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                if (activityManager.isLowRamDevice()) {
                    return true;
                }
            }

            // Check available memory
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            // Consider low memory if less than 512MB available or low memory threshold reached
            return memoryInfo.availMem < 512 * 1024 * 1024 || memoryInfo.lowMemory;

        } catch (Exception e) {
            NRLog.w("Failed to detect memory conditions: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String toString() {
        return "NRVideoConfiguration{" +
                "region='" + region + '\'' +
                ", harvestCycle=" + harvestCycleSeconds + "s" +
                ", liveHarvestCycle=" + liveHarvestCycleSeconds + "s" +
                ", regularBatchSize=" + (regularBatchSizeBytes / 1024) + "KB" +
                ", liveBatchSize=" + (liveBatchSizeBytes / 1024) + "KB" +
                ", maxDeadLetterSize=" + maxDeadLetterSize +
                ", memoryOptimized=" + memoryOptimized +
                ", isTV=" + isTV +
                ", debugLogging=" + debugLoggingEnabled +
                '}';
    }
}
