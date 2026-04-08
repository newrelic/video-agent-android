package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.utils.NRLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Applies a list of ObfuscationRules to a batch of events.
 *
 * Extracted into its own class so it can be unit-tested independently of
 * OptimizedHttpClient (which requires Android context, HTTP connections, etc.).
 */
public class ObfuscationEngine {

    // Static utility class — no instances needed.
    private ObfuscationEngine() {}

    /**
     * Returns a new list of events with all obfuscation rules applied to every
     * string-valued attribute. The original list and maps are never modified.
     *
     * @param events The raw event batch (may include QOE, regular, dead-letter events).
     * @param rules  The rules from NRVideoConfiguration. Empty list is a no-op.
     * @return A new list with obfuscated copies, or the original list if rules is empty.
     */
    public static List<Map<String, Object>> apply(
            List<Map<String, Object>> events,
            List<ObfuscationRule> rules) {

        if (rules == null || rules.isEmpty()) {
            return events; // fast path — zero overhead when not configured
        }
        if (events == null || events.isEmpty()) {
            return events;
        }

        List<Map<String, Object>> result = new ArrayList<>(events.size());

        for (Map<String, Object> event : events) {
            // Shallow copy — safe because String is immutable (same in Java and JS).
            Map<String, Object> copy = new HashMap<>(event);

            for (Map.Entry<String, Object> entry : copy.entrySet()) {
                if (entry.getValue() instanceof String) {
                    String value = (String) entry.getValue();

                    for (ObfuscationRule rule : rules) {
                        try {
                            // quoteReplacement treats '$' and '\' in the replacement as
                            // plain characters, not regex back-references.
                            value = rule.regex.matcher(value)
                                              .replaceAll(Matcher.quoteReplacement(rule.replacement));
                        } catch (Exception e) {
                            NRLog.w("ObfuscationEngine: rule failed on key '"
                                    + entry.getKey() + "': " + e.getMessage());
                        }
                    }

                    entry.setValue(value);
                }
            }

            result.add(copy);
        }

        return result;
    }
}
