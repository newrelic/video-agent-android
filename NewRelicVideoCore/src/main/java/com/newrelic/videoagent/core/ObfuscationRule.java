package com.newrelic.videoagent.core;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single obfuscation rule — a regex pattern paired with a replacement string.
 *
 * React analogy: think of this as a TypeScript type:
 *   type ObfuscationRule = { regex: RegExp; replacement: string }
 *
 * Usage:
 *   new ObfuscationRule("account-\\d+", "ACCOUNT_ID")
 *   new ObfuscationRule("/users/[^\"/]+", "/users/USER_ID")
 *   new ObfuscationRule("token=[^&\"]+", "token=REDACTED")
 */
public class ObfuscationRule {

    // 'final' in Java == 'const' in JS — these fields can never be reassigned after construction.
    public final Pattern regex;
    public final String replacement;

    /**
     * @param pattern     Regex pattern string, e.g. "account-\\d+"
     *                    Note: Java strings need double-backslash for regex escapes,
     *                    same as writing new RegExp("account-\\d+") in JS.
     * @param replacement String to substitute for each match, e.g. "ACCOUNT_ID".
     *                    Pass "" (empty string) to delete matched content entirely.
     */
    public ObfuscationRule(String pattern, String replacement) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("ObfuscationRule: pattern cannot be null or empty");
        }
        if (replacement == null) {
            // We allow "" (delete matches) but not null — null would NPE at apply time.
            throw new IllegalArgumentException("ObfuscationRule: replacement cannot be null. Use \"\" to delete matches.");
        }

        this.replacement = replacement;

        // Compile the regex eagerly here in the constructor.
        // React analogy: like validating props in a constructor — fail fast at setup time,
        // not silently during a harvest cycle when an event is about to be sent.
        try {
            this.regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                "ObfuscationRule: invalid regex pattern \"" + pattern + "\": " + e.getMessage(), e
            );
        }
    }
}
