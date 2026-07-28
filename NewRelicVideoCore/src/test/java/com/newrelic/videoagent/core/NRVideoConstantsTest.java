package com.newrelic.videoagent.core;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.*;

/**
 * Unit tests for NRVideoConstants.
 */
public class NRVideoConstantsTest {

    @Test
    public void testDeviceTypeConstants() {
        assertEquals("AndroidTV", NRVideoConstants.ANDROID_TV);
        assertEquals("Mobile", NRVideoConstants.MOBILE);
    }

    @Test
    public void testEventTypeConstants() {
        assertEquals("live", NRVideoConstants.EVENT_TYPE_LIVE);
        assertEquals("ondemand", NRVideoConstants.EVENT_TYPE_ONDEMAND);
    }

    @Test
    public void testDeprecatedCategoryConstants() {
        assertEquals("default", NRVideoConstants.CATEGORY_DEFAULT);
        assertEquals("normal", NRVideoConstants.CATEGORY_NORMAL);
    }

    @Test
    public void testHealthStatusConstants() {
        assertEquals("IDLE", NRVideoConstants.HEALTH_IDLE);
        assertEquals("HEALTHY", NRVideoConstants.HEALTH_HEALTHY);
        assertEquals("WARNING", NRVideoConstants.HEALTH_WARNING);
        assertEquals("DEGRADED", NRVideoConstants.HEALTH_DEGRADED);
        assertEquals("CRITICAL", NRVideoConstants.HEALTH_CRITICAL);
    }

    @Test
    public void testDeviceTypeConstantsAreUnique() {
        assertNotEquals(NRVideoConstants.ANDROID_TV, NRVideoConstants.MOBILE);
    }

    @Test
    public void testEventTypeConstantsAreUnique() {
        assertNotEquals(NRVideoConstants.EVENT_TYPE_LIVE, NRVideoConstants.EVENT_TYPE_ONDEMAND);
    }

    @Test
    public void testHealthStatusConstantsAreUnique() {
        String[] healthStatuses = {
            NRVideoConstants.HEALTH_IDLE,
            NRVideoConstants.HEALTH_HEALTHY,
            NRVideoConstants.HEALTH_WARNING,
            NRVideoConstants.HEALTH_DEGRADED,
            NRVideoConstants.HEALTH_CRITICAL
        };

        for (int i = 0; i < healthStatuses.length; i++) {
            for (int j = i + 1; j < healthStatuses.length; j++) {
                assertNotEquals(healthStatuses[i], healthStatuses[j]);
            }
        }
    }

    @Test
    public void testConstantsAreNotNull() {
        assertNotNull(NRVideoConstants.ANDROID_TV);
        assertNotNull(NRVideoConstants.MOBILE);
        assertNotNull(NRVideoConstants.EVENT_TYPE_LIVE);
        assertNotNull(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        assertNotNull(NRVideoConstants.CATEGORY_DEFAULT);
        assertNotNull(NRVideoConstants.CATEGORY_NORMAL);
        assertNotNull(NRVideoConstants.HEALTH_IDLE);
        assertNotNull(NRVideoConstants.HEALTH_HEALTHY);
        assertNotNull(NRVideoConstants.HEALTH_WARNING);
        assertNotNull(NRVideoConstants.HEALTH_DEGRADED);
        assertNotNull(NRVideoConstants.HEALTH_CRITICAL);
    }

    @Test
    public void testConstantsAreNotEmpty() {
        assertFalse(NRVideoConstants.ANDROID_TV.isEmpty());
        assertFalse(NRVideoConstants.MOBILE.isEmpty());
        assertFalse(NRVideoConstants.EVENT_TYPE_LIVE.isEmpty());
        assertFalse(NRVideoConstants.EVENT_TYPE_ONDEMAND.isEmpty());
        assertFalse(NRVideoConstants.CATEGORY_DEFAULT.isEmpty());
        assertFalse(NRVideoConstants.CATEGORY_NORMAL.isEmpty());
        assertFalse(NRVideoConstants.HEALTH_IDLE.isEmpty());
        assertFalse(NRVideoConstants.HEALTH_HEALTHY.isEmpty());
        assertFalse(NRVideoConstants.HEALTH_WARNING.isEmpty());
        assertFalse(NRVideoConstants.HEALTH_DEGRADED.isEmpty());
        assertFalse(NRVideoConstants.HEALTH_CRITICAL.isEmpty());
    }

    @Test
    public void testEventTypesAreLowercase() {
        assertEquals(NRVideoConstants.EVENT_TYPE_LIVE.toLowerCase(), NRVideoConstants.EVENT_TYPE_LIVE);
        assertEquals(NRVideoConstants.EVENT_TYPE_ONDEMAND.toLowerCase(), NRVideoConstants.EVENT_TYPE_ONDEMAND);
    }

    @Test
    public void testHealthStatusesAreUppercase() {
        assertEquals(NRVideoConstants.HEALTH_IDLE.toUpperCase(), NRVideoConstants.HEALTH_IDLE);
        assertEquals(NRVideoConstants.HEALTH_HEALTHY.toUpperCase(), NRVideoConstants.HEALTH_HEALTHY);
        assertEquals(NRVideoConstants.HEALTH_WARNING.toUpperCase(), NRVideoConstants.HEALTH_WARNING);
        assertEquals(NRVideoConstants.HEALTH_DEGRADED.toUpperCase(), NRVideoConstants.HEALTH_DEGRADED);
        assertEquals(NRVideoConstants.HEALTH_CRITICAL.toUpperCase(), NRVideoConstants.HEALTH_CRITICAL);
    }

    @Test
    public void testConstructorThrowsCorrectException() {
        try {
            Constructor<NRVideoConstants> constructor = NRVideoConstants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Expected AssertionError to be thrown");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof AssertionError);
            assertEquals("Constants class should not be instantiated", e.getCause().getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName());
        }
    }

    @Test
    public void testDeviceTypeAndroidTV() {
        String deviceType = NRVideoConstants.ANDROID_TV;
        assertEquals("AndroidTV", deviceType);
        assertTrue(deviceType.contains("Android"));
        assertTrue(deviceType.contains("TV"));
    }

    @Test
    public void testHealthStatusOrder() {
        // Health statuses should represent increasing severity levels
        assertNotNull(NRVideoConstants.HEALTH_IDLE);
        assertNotNull(NRVideoConstants.HEALTH_HEALTHY);
        assertNotNull(NRVideoConstants.HEALTH_WARNING);
        assertNotNull(NRVideoConstants.HEALTH_DEGRADED);
        assertNotNull(NRVideoConstants.HEALTH_CRITICAL);
    }

    @Test
    public void testDeprecatedConstantsStillAccessible() {
        // Even though deprecated, they should still be accessible for backward compatibility
        @SuppressWarnings("deprecation")
        String categoryDefault = NRVideoConstants.CATEGORY_DEFAULT;
        @SuppressWarnings("deprecation")
        String categoryNormal = NRVideoConstants.CATEGORY_NORMAL;

        assertEquals("default", categoryDefault);
        assertEquals("normal", categoryNormal);
    }

    @Test
    public void testCategoryConstantsAreDifferent() {
        @SuppressWarnings("deprecation")
        String cat1 = NRVideoConstants.CATEGORY_DEFAULT;
        @SuppressWarnings("deprecation")
        String cat2 = NRVideoConstants.CATEGORY_NORMAL;

        assertNotEquals(cat1, cat2);
    }
}
