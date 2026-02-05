package com.newrelic.videoagent.core.config;

/**
 * Configuration manager for New Relic Video Agent functionality.
 * Default values are sourced from local.properties, can be overridden by client configuration.
 */
public class NRVideoConfig {

    private static final NRVideoConfig INSTANCE = new NRVideoConfig();

    // Configuration value - set from local.properties via BuildConfig
    private boolean qoeAggregate;
    private boolean initialized = false;

    private NRVideoConfig() {
    }

    /**
     * Get the singleton instance of NRVideoConfig
     * @return NRVideoConfig instance
     */
    public static NRVideoConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Check if QOE_AGGREGATE events should be sent during harvest cycles
     * @return true if QOE_AGGREGATE should be sent, false otherwise
     */
    public boolean isQoeAggregateEnabled() {
        if (!initialized) {
            throw new IllegalStateException("NRVideoConfig not initialized! Call initializeDefaults() first.");
        }
        return qoeAggregate;
    }

    /**
     * Set whether QOE_AGGREGATE events should be sent during harvest cycles
     * This will be called with client configuration in the future
     * @param enabled true to enable QOE_AGGREGATE, false to disable
     */
    public void setQoeAggregateEnabled(boolean enabled) {
        this.qoeAggregate = enabled;
    }

    /**
     * Initialize configuration with default values from local.properties
     * This method should be called during app initialization
     * @param defaultQoeAggregateEnabled Default value for QOE aggregate from BuildConfig
     */
    public void initializeDefaults(boolean defaultQoeAggregateEnabled) {
        if (!initialized) {
            this.qoeAggregate = defaultQoeAggregateEnabled;
            this.initialized = true;
        }
    }

    /**
     * Initialize configuration with client settings
     * @param clientQoeAggregateEnabled QOE aggregate setting from client (null if not provided)
     */
    public void initializeFromClient(Boolean clientQoeAggregateEnabled) {
        // If client provides a value, use it; otherwise keep current default
        if (clientQoeAggregateEnabled != null) {
            this.qoeAggregate = clientQoeAggregateEnabled;
        }
    }
}