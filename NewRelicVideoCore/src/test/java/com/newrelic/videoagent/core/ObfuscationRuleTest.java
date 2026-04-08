package com.newrelic.videoagent.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for ObfuscationRule construction and validation.
 *
 * These are pure JUnit 4 tests — no Android context needed because ObfuscationRule
 * only uses java.util.regex.Pattern, which is plain Java.
 */
public class ObfuscationRuleTest {

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void validRule_compilesSuccessfully() {
        ObfuscationRule rule = new ObfuscationRule("account-\\d+", "ACCOUNT_ID");

        assertNotNull(rule.regex);
        assertEquals("ACCOUNT_ID", rule.replacement);
    }

    @Test
    public void validRule_emptyReplacement_isAllowed() {
        // Empty string means "delete matches" — a legitimate use case.
        ObfuscationRule rule = new ObfuscationRule("secret-\\w+", "");

        assertNotNull(rule.regex);
        assertEquals("", rule.replacement);
    }

    @Test
    public void validRule_replacementWithDollarSign_isAllowed() {
        // '$' is special in Java regex replacements, but ObfuscationEngine guards against
        // that with Matcher.quoteReplacement(). The rule itself should accept any string.
        ObfuscationRule rule = new ObfuscationRule("price-\\d+", "$PRICE");

        assertEquals("$PRICE", rule.replacement);
    }

    @Test
    public void validRule_replacementWithBackslash_isAllowed() {
        // Same as above — '\' is special in replacements but quoteReplacement handles it.
        ObfuscationRule rule = new ObfuscationRule("path-\\w+", "\\REDACTED");

        assertEquals("\\REDACTED", rule.replacement);
    }

    @Test
    public void validRule_complexPattern_compilesSuccessfully() {
        // A realistic URL masking pattern
        ObfuscationRule rule = new ObfuscationRule("/users/[^\"/]+", "/users/USER_ID");

        assertNotNull(rule.regex);
        // Verify the compiled regex actually matches what we expect
        assertTrue(rule.regex.matcher("/users/john_doe/profile").find());
        assertFalse(rule.regex.matcher("/products/shoes").find());
    }

    @Test
    public void validRule_patternWithFlags_compilesSuccessfully() {
        // Patterns with inline flags like (?i) for case-insensitive
        ObfuscationRule rule = new ObfuscationRule("(?i)token=[^&]+", "token=REDACTED");

        assertNotNull(rule.regex);
        assertTrue(rule.regex.matcher("TOKEN=abc123").find());
        assertTrue(rule.regex.matcher("token=abc123").find());
    }

    // -------------------------------------------------------------------------
    // Null / empty pattern
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void nullPattern_throwsIllegalArgumentException() {
        new ObfuscationRule(null, "REPLACEMENT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyPattern_throwsIllegalArgumentException() {
        new ObfuscationRule("", "REPLACEMENT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankPattern_whitespaceOnly_throwsIllegalArgumentException() {
        // "   " is technically non-empty but trim() makes it empty — should still reject.
        new ObfuscationRule("   ", "REPLACEMENT");
    }

    // -------------------------------------------------------------------------
    // Null replacement
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void nullReplacement_throwsIllegalArgumentException() {
        // null replacement would NPE silently at apply time — reject it eagerly.
        new ObfuscationRule("account-\\d+", null);
    }

    // -------------------------------------------------------------------------
    // Invalid regex
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void invalidRegex_unclosedGroup_throwsIllegalArgumentException() {
        // "(unclosed" is a malformed regex — should fail at construction, not at harvest.
        new ObfuscationRule("(unclosed", "REPLACEMENT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRegex_danglingQuantifier_throwsIllegalArgumentException() {
        // "*" with nothing to quantify is invalid
        new ObfuscationRule("*invalid", "REPLACEMENT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRegex_unclosedBracket_throwsIllegalArgumentException() {
        new ObfuscationRule("[unclosed", "REPLACEMENT");
    }

    // -------------------------------------------------------------------------
    // Pattern correctness (verify the compiled regex behaves as expected)
    // -------------------------------------------------------------------------

    @Test
    public void compiledPattern_matchesExpectedInput() {
        ObfuscationRule rule = new ObfuscationRule("account-\\d+", "ACCOUNT_ID");

        assertTrue(rule.regex.matcher("account-83729").matches());
        assertTrue(rule.regex.matcher("account-1").matches());
        assertFalse(rule.regex.matcher("account-abc").matches()); // letters, not digits
        assertFalse(rule.regex.matcher("account-").matches());    // no digits at all
    }

    @Test
    public void compiledPattern_tokenMasking_matchesExpectedInput() {
        ObfuscationRule rule = new ObfuscationRule("token=[^&\"]+", "token=REDACTED");

        assertTrue(rule.regex.matcher("token=abc123xyz").find());
        assertFalse(rule.regex.matcher("token=").find()); // empty value — no match
    }
}
