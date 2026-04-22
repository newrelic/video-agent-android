package com.newrelic.videoagent.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for ObfuscationEngine.apply() — the core logic that masks event attributes.
 *
 * Grouped into sections:
 *   1. No-op / fast-path cases
 *   2. Basic replacement
 *   3. Multiple rules
 *   4. Non-string value handling
 *   5. Immutability — originals must never be mutated
 *   6. Corner cases (special chars in replacement, empty strings, null values, etc.)
 *   7. Realistic end-to-end scenarios
 */
public class ObfuscationEngineTest {

    // =========================================================================
    // 1. No-op / fast-path cases
    // =========================================================================

    @Test
    public void noRules_returnsOriginalListUnchanged() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "https://cdn.com/users/john/video.mp4");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, Collections.emptyList());

        // With no rules the exact same list reference is returned — zero overhead.
        assertSame(events, result);
    }

    @Test
    public void nullRules_returnsOriginalListUnchanged() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "https://cdn.com/secret");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, null);

        assertSame(events, result);
    }

    @Test
    public void emptyEventList_returnsOriginalEmptyList() {
        List<Map<String, Object>> events = new ArrayList<>();
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertSame(events, result);
    }

    @Test
    public void nullEventList_returnsNull() {
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(null, rules);

        assertNull(result);
    }

    // =========================================================================
    // 2. Basic replacement
    // =========================================================================

    @Test
    public void singleRule_matchingValue_isReplaced() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "https://cdn.com/account-83729/video.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("https://cdn.com/ACCOUNT_ID/video.mp4", result.get(0).get("contentSrc"));
    }

    @Test
    public void singleRule_noMatch_valueUnchanged() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "https://cdn.com/video.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("https://cdn.com/video.mp4", result.get(0).get("contentSrc"));
    }

    @Test
    public void singleRule_multipleMatchesInSameValue_allReplaced() {
        // regex replaceAll — every occurrence is replaced, not just the first.
        List<Map<String, Object>> events = singleEvent("contentSrc", "account-111/sub/account-222");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("ACCOUNT_ID/sub/ACCOUNT_ID", result.get(0).get("contentSrc"));
    }

    @Test
    public void singleRule_entireValueMatches_wholeValueReplaced() {
        List<Map<String, Object>> events = singleEvent("userId", "john_doe_123");
        List<ObfuscationRule> rules = rules("john_doe_\\d+", "USER_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("USER_ID", result.get(0).get("userId"));
    }

    @Test
    public void singleRule_appliedAcrossAllEventsInBatch() {
        Map<String, Object> e1 = event("contentSrc", "cdn.com/account-1/v.mp4");
        Map<String, Object> e2 = event("contentSrc", "cdn.com/account-2/v.mp4");
        Map<String, Object> e3 = event("contentSrc", "cdn.com/no-match/v.mp4");
        List<Map<String, Object>> events = Arrays.asList(e1, e2, e3);
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("cdn.com/ACCOUNT_ID/v.mp4",  result.get(0).get("contentSrc"));
        assertEquals("cdn.com/ACCOUNT_ID/v.mp4",  result.get(1).get("contentSrc"));
        assertEquals("cdn.com/no-match/v.mp4",     result.get(2).get("contentSrc")); // unchanged
    }

    @Test
    public void singleRule_allStringAttributesInEventAreObfuscated() {
        // The rule should run on every string value in the map, not just "contentSrc".
        Map<String, Object> ev = new HashMap<>();
        ev.put("contentSrc",   "https://cdn.com/account-99/video");
        ev.put("contentTitle", "Show for account-99 subscribers");
        ev.put("eventType",    "CONTENT_START");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);
        Map<String, Object> out = result.get(0);

        assertEquals("https://cdn.com/ACCOUNT_ID/video", out.get("contentSrc"));
        assertEquals("Show for ACCOUNT_ID subscribers",  out.get("contentTitle"));
        assertEquals("CONTENT_START",                    out.get("eventType")); // no match — unchanged
    }

    // =========================================================================
    // 3. Multiple rules
    // =========================================================================

    @Test
    public void multipleRules_allAppliedInOrder() {
        Map<String, Object> ev = event("url", "https://cdn.com/account-42/token=secret99");
        List<ObfuscationRule> rules = Arrays.asList(
            new ObfuscationRule("account-\\d+", "ACCOUNT_ID"),
            new ObfuscationRule("token=[^&\"]+", "token=REDACTED")
        );

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals("https://cdn.com/ACCOUNT_ID/token=REDACTED", result.get(0).get("url"));
    }

    @Test
    public void multipleRules_orderMatters_secondRuleSeesOutputOfFirst() {
        // Rule 1 turns "john" → "USER", then Rule 2 turns "USER_profile" → "PROFILE".
        // If applied to the original (not chained), Rule 2 would never match "USER_profile".
        Map<String, Object> ev = event("path", "/john_profile");
        List<ObfuscationRule> rules = Arrays.asList(
            new ObfuscationRule("john", "USER"),       // /john_profile → /USER_profile
            new ObfuscationRule("USER_profile", "PROFILE") // /USER_profile → /PROFILE
        );

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals("/PROFILE", result.get(0).get("path"));
    }

    // =========================================================================
    // 4. Non-string value handling
    // =========================================================================

    @Test
    public void integerValues_areNotObfuscated() {
        Map<String, Object> ev = event("bitrate", 5000000); // Integer, not String
        List<ObfuscationRule> rules = rules("\\d+", "REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals(5000000, result.get(0).get("bitrate")); // unchanged
    }

    @Test
    public void longValues_areNotObfuscated() {
        Map<String, Object> ev = event("timestamp", 1712345678000L);
        List<ObfuscationRule> rules = rules("\\d+", "REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals(1712345678000L, result.get(0).get("timestamp"));
    }

    @Test
    public void doubleValues_areNotObfuscated() {
        Map<String, Object> ev = event("bufferLength", 2.5);
        List<ObfuscationRule> rules = rules("\\d+", "REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals(2.5, result.get(0).get("bufferLength"));
    }

    @Test
    public void booleanValues_areNotObfuscated() {
        Map<String, Object> ev = event("contentIsLive", true);
        List<ObfuscationRule> rules = rules("true", "REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals(true, result.get(0).get("contentIsLive")); // still Boolean, not a String
    }

    @Test
    public void nullValue_isSkipped_noNullPointerException() {
        // A map entry with a null value must not throw — instanceof String returns false for null.
        Map<String, Object> ev = new HashMap<>();
        ev.put("contentId", null);
        ev.put("contentSrc", "https://cdn.com/account-5/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertNull(result.get(0).get("contentId"));                              // null preserved
        assertEquals("https://cdn.com/ACCOUNT_ID/v.mp4", result.get(0).get("contentSrc")); // string replaced
    }

    // =========================================================================
    // 5. Immutability — originals must never be mutated
    // =========================================================================

    @Test
    public void originalList_isNotMutated() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "cdn.com/account-1/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        ObfuscationEngine.apply(events, rules);

        // The original list must still hold the unobfuscated value.
        assertEquals("cdn.com/account-1/v.mp4", events.get(0).get("contentSrc"));
    }

    @Test
    public void originalMaps_areNotMutated() {
        Map<String, Object> original = event("contentSrc", "cdn.com/account-1/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        ObfuscationEngine.apply(Collections.singletonList(original), rules);

        // The original map object must be untouched.
        assertEquals("cdn.com/account-1/v.mp4", original.get("contentSrc"));
    }

    @Test
    public void returnedList_isDifferentInstance() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "cdn.com/account-1/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertNotSame(events, result);           // different list
        assertNotSame(events.get(0), result.get(0)); // different map
    }

    @Test
    public void immutability_critical_deadLetterRetryGetsCleanOriginal() {
        // Simulates what HarvestManager does: pass the same events list to sendEvents()
        // and, on failure, to the dead-letter handler. The dead-letter handler must see
        // unobfuscated values so the next retry obfuscates them correctly.
        Map<String, Object> original = event("contentSrc", "cdn.com/account-99/v.mp4");
        List<Map<String, Object>> events = Collections.singletonList(original);
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        // Simulate first send attempt (obfuscate + send)
        List<Map<String, Object>> firstAttempt = ObfuscationEngine.apply(events, rules);
        assertEquals("cdn.com/ACCOUNT_ID/v.mp4", firstAttempt.get(0).get("contentSrc"));

        // Simulate dead-letter retry: apply again on the ORIGINAL list
        List<Map<String, Object>> retryAttempt = ObfuscationEngine.apply(events, rules);
        assertEquals("cdn.com/ACCOUNT_ID/v.mp4", retryAttempt.get(0).get("contentSrc")); // still correct
        assertEquals("cdn.com/account-99/v.mp4", events.get(0).get("contentSrc"));       // original clean
    }

    // =========================================================================
    // 6. Corner cases
    // =========================================================================

    @Test
    public void emptyStringValue_ruleDoesNotMatch_valueUnchanged() {
        List<Map<String, Object>> events = singleEvent("contentTitle", "");
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("", result.get(0).get("contentTitle"));
    }

    @Test
    public void emptyReplacement_deletesMatchedContent() {
        List<Map<String, Object>> events = singleEvent("url", "https://cdn.com/account-42/video");
        List<ObfuscationRule> rules = rules("account-\\d+", "");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("https://cdn.com//video", result.get(0).get("url"));
    }

    @Test
    public void replacementWithDollarSign_treatedAsLiteral() {
        // Without Matcher.quoteReplacement(), "$ID" would be treated as a back-reference
        // and throw IllegalArgumentException or produce wrong output.
        List<Map<String, Object>> events = singleEvent("contentSrc", "cdn.com/account-7/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "$ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        // '$' must appear literally in the output, not cause an exception.
        assertEquals("cdn.com/$ACCOUNT_ID/v.mp4", result.get(0).get("contentSrc"));
    }

    @Test
    public void replacementWithBackslash_treatedAsLiteral() {
        List<Map<String, Object>> events = singleEvent("contentSrc", "cdn.com/account-7/v.mp4");
        List<ObfuscationRule> rules = rules("account-\\d+", "\\REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals("cdn.com/\\REDACTED/v.mp4", result.get(0).get("contentSrc"));
    }

    @Test
    public void eventWithOnlyNonStringValues_copiedAsIs() {
        Map<String, Object> ev = new HashMap<>();
        ev.put("bitrate",    5000000);
        ev.put("timestamp",  1712345678000L);
        ev.put("isLive",     false);
        List<ObfuscationRule> rules = rules("\\d+", "REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);

        assertEquals(5000000,          result.get(0).get("bitrate"));
        assertEquals(1712345678000L,   result.get(0).get("timestamp"));
        assertEquals(false,            result.get(0).get("isLive"));
    }

    @Test
    public void mixedBatch_regularAndQoeEvents_allObfuscated() {
        // Simulates the real harvest batch: regular events + QOE event injected afterwards.
        Map<String, Object> contentStart = new HashMap<>();
        contentStart.put("eventType", "CONTENT_START");
        contentStart.put("contentSrc", "https://cdn.com/account-10/video.mp4");

        Map<String, Object> qoeEvent = new HashMap<>();
        qoeEvent.put("eventType", "QOE_AGGREGATE");
        qoeEvent.put("contentSrc", "https://cdn.com/account-10/video.mp4");
        qoeEvent.put("totalPlaytime", 30000L); // Long — must not be touched

        List<Map<String, Object>> batch = Arrays.asList(contentStart, qoeEvent);
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(batch, rules);

        assertEquals("https://cdn.com/ACCOUNT_ID/video.mp4", result.get(0).get("contentSrc"));
        assertEquals("https://cdn.com/ACCOUNT_ID/video.mp4", result.get(1).get("contentSrc"));
        assertEquals(30000L, result.get(1).get("totalPlaytime")); // Long untouched
    }

    @Test
    public void resultListHasSameSizeAsInput() {
        List<Map<String, Object>> events = Arrays.asList(
            event("contentSrc", "cdn.com/account-1/v.mp4"),
            event("contentSrc", "cdn.com/account-2/v.mp4"),
            event("contentSrc", "cdn.com/account-3/v.mp4")
        );
        List<ObfuscationRule> rules = rules("account-\\d+", "ACCOUNT_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals(3, result.size());
    }

    // =========================================================================
    // 7. Realistic end-to-end scenarios
    // =========================================================================

    @Test
    public void scenario_maskUserIdInUrl() {
        List<Map<String, Object>> events = singleEvent(
            "contentSrc", "https://stream.example.com/users/john_doe_123/live.m3u8"
        );
        List<ObfuscationRule> rules = rules("/users/[^\"/]+", "/users/USER_ID");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals(
            "https://stream.example.com/users/USER_ID/live.m3u8",
            result.get(0).get("contentSrc")
        );
    }

    @Test
    public void scenario_maskAuthToken() {
        List<Map<String, Object>> events = singleEvent(
            "contentSrc", "https://cdn.com/video.mp4?token=eyJhbGciOiJIUzI1NiJ9&quality=HD"
        );
        List<ObfuscationRule> rules = rules("token=[^&\"]+", "token=REDACTED");

        List<Map<String, Object>> result = ObfuscationEngine.apply(events, rules);

        assertEquals(
            "https://cdn.com/video.mp4?token=REDACTED&quality=HD",
            result.get(0).get("contentSrc")
        );
    }

    @Test
    public void scenario_threeRulesOnRealWorldEvent() {
        Map<String, Object> ev = new HashMap<>();
        ev.put("eventType",    "CONTENT_START");
        ev.put("contentSrc",   "https://cdn.com/account-42/users/jane/token=secret99&q=HD");
        ev.put("contentTitle", "Premium content for account-42");
        ev.put("bitrate",      4500000); // non-string, must be untouched
        ev.put("timestamp",    1712345678000L);

        List<ObfuscationRule> rules = Arrays.asList(
            new ObfuscationRule("account-\\d+",  "ACCOUNT_ID"),
            new ObfuscationRule("/users/[^/\"?&]+", "/users/USER_ID"),
            new ObfuscationRule("token=[^&\"]+", "token=REDACTED")
        );

        List<Map<String, Object>> result = ObfuscationEngine.apply(Collections.singletonList(ev), rules);
        Map<String, Object> out = result.get(0);

        assertEquals("CONTENT_START", out.get("eventType")); // no match
        assertEquals(
            "https://cdn.com/ACCOUNT_ID/users/USER_ID/token=REDACTED&q=HD",
            out.get("contentSrc")
        );
        assertEquals("Premium content for ACCOUNT_ID",  out.get("contentTitle"));
        assertEquals(4500000,          out.get("bitrate"));    // Integer untouched
        assertEquals(1712345678000L,   out.get("timestamp"));  // Long untouched
    }

    // =========================================================================
    // Helpers — keep test bodies readable
    // =========================================================================

    /** Creates a single-entry event list with one key-value pair. */
    private List<Map<String, Object>> singleEvent(String key, Object value) {
        return Collections.singletonList(event(key, value));
    }

    /** Creates a single event map with one key-value pair. */
    private Map<String, Object> event(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /** Creates a single-rule list from a pattern and replacement string. */
    private List<ObfuscationRule> rules(String pattern, String replacement) {
        return Collections.singletonList(new ObfuscationRule(pattern, replacement));
    }
}
