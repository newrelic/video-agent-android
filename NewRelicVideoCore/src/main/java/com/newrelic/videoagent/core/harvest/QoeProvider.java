package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

/**
 * Provider interface for QOE_AGGREGATE event generation at harvest time.
 * Registered by video trackers and invoked by HarvestManager during harvest cycles.
 */
public interface QoeProvider {

    /**
     * Called by HarvestManager during harvest to check if QOE should be generated.
     *
     * @param batch The current harvest batch containing VideoAction events
     * @param harvestCycleNumber The current harvest cycle number (1-based)
     * @return QOE_AGGREGATE event map if it should be generated, null otherwise
     */
    Map<String, Object> generateQoeIfNeeded(List<Map<String, Object>> batch, int harvestCycleNumber);

    /**
     * Called when the provider should be unregistered (e.g., video ended).
     */
    void unregister();
}
