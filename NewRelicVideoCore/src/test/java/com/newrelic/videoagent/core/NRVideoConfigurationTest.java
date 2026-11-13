package com.newrelic.videoagent.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.newrelic.agent.android.AgentConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Covers the base-Android-agent config fallback added to NRVideoConfiguration:
 * when the caller omits an explicit applicationToken, build() falls back to
 * the real AgentConfiguration.getInstance() singleton (via reflection, no
 * compile-time dependency on the base agent from production code — this test
 * module adds it as testImplementation only, to exercise the real class
 * rather than a hand-rolled stub).
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoConfigurationTest {

    @Before
    @After
    public void resetBaseAgentConfiguration() {
        // AgentConfiguration.getInstance() is a process-wide singleton — reset
        // between tests (and once more after) so no test leaks its token into
        // another, same as a fresh app process would have a clean slate.
        AgentConfiguration.getInstance().setApplicationToken(null);
    }

    @Test
    public void shouldThrowWhenNoExplicitTokenAndBaseAgentNotStarted() {
        assertThrows(IllegalArgumentException.class, () ->
            new NRVideoConfiguration.Builder().build()
        );
    }

    @Test
    public void shouldFallBackToBaseAgentTokenWhenNoExplicitTokenGiven() {
        AgentConfiguration.getInstance().setApplicationToken("US-base-agent-token-1234567890");

        NRVideoConfiguration config = new NRVideoConfiguration.Builder().build();

        assertEquals("US-base-agent-token-1234567890", config.getApplicationToken());
    }

    @Test
    public void shouldFallBackViaStringConstructorWhenTokenIsNull() {
        AgentConfiguration.getInstance().setApplicationToken("US-base-agent-token-1234567890");

        NRVideoConfiguration config = new NRVideoConfiguration.Builder(null).build();

        assertEquals("US-base-agent-token-1234567890", config.getApplicationToken());
    }

    @Test
    public void shouldLetExplicitTokenOverrideBaseAgentFallback() {
        AgentConfiguration.getInstance().setApplicationToken("US-base-agent-token-1234567890");

        NRVideoConfiguration config = new NRVideoConfiguration.Builder("EU-explicit-token-9876543210").build();

        assertEquals("EU-explicit-token-9876543210", config.getApplicationToken());
    }

    @Test
    public void shouldUseBaseAgentCollectorHostWhenCollectorAddressNotExplicitlySet() {
        AgentConfiguration.getInstance().setApplicationToken("US-base-agent-token-1234567890");
        String baseAgentCollectorHost = AgentConfiguration.getInstance().getCollectorHost();

        NRVideoConfiguration config = new NRVideoConfiguration.Builder().build();

        assertEquals(baseAgentCollectorHost, config.getCollectorAddress());
    }

    @Test
    public void shouldLetExplicitCollectorAddressOverrideBaseAgentFallback() {
        AgentConfiguration.getInstance().setApplicationToken("US-base-agent-token-1234567890");

        NRVideoConfiguration config = new NRVideoConfiguration.Builder()
            .withCollectorAddress("explicit-collector.newrelic.com")
            .build();

        assertEquals("explicit-collector.newrelic.com", config.getCollectorAddress());
    }

    @Test
    public void shouldStillWorkWithExplicitTokenWhenBaseAgentNeverStarted() {
        // Baseline regression check: the common case (no base agent involved
        // at all) must behave exactly as before this change.
        NRVideoConfiguration config = new NRVideoConfiguration.Builder("US-standalone-token-1234567890").build();

        assertEquals("US-standalone-token-1234567890", config.getApplicationToken());
    }
}
