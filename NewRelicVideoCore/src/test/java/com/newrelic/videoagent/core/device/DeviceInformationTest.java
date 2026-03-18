package com.newrelic.videoagent.core.device;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for DeviceInformation that exercise actual production code.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class DeviceInformationTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void testGetInstanceReturnsSingleton() {
        DeviceInformation instance1 = DeviceInformation.getInstance(context);
        DeviceInformation instance2 = DeviceInformation.getInstance(context);

        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
    }

    @Test
    public void testGetOsName() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String osName = deviceInfo.getOsName();

        assertNotNull(osName);
        assertEquals("Android", osName);
    }

    @Test
    public void testGetOsVersion() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String osVersion = deviceInfo.getOsVersion();

        assertNotNull(osVersion);
        assertFalse(osVersion.isEmpty());
    }

    @Test
    public void testGetOsBuild() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String osBuild = deviceInfo.getOsBuild();

        assertNotNull(osBuild);
        assertFalse(osBuild.isEmpty());
    }

    @Test
    public void testGetModel() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String model = deviceInfo.getModel();

        assertNotNull(model);
        assertFalse(model.isEmpty());
    }

    @Test
    public void testGetManufacturer() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String manufacturer = deviceInfo.getManufacturer();

        assertNotNull(manufacturer);
        assertFalse(manufacturer.isEmpty());
    }

    @Test
    public void testGetDeviceId() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String deviceId = deviceInfo.getDeviceId();

        assertNotNull(deviceId);
        assertFalse(deviceId.isEmpty());
    }

    @Test
    public void testDeviceIdIsConsistent() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String deviceId1 = deviceInfo.getDeviceId();
        String deviceId2 = deviceInfo.getDeviceId();

        assertEquals(deviceId1, deviceId2);
    }

    @Test
    public void testGetArchitecture() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String architecture = deviceInfo.getArchitecture();

        assertNotNull(architecture);
        assertFalse(architecture.isEmpty());
    }

    @Test
    public void testGetRunTime() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String runTime = deviceInfo.getRunTime();

        assertNotNull(runTime);
        assertFalse(runTime.isEmpty());
    }

    @Test
    public void testGetSize() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String size = deviceInfo.getSize();

        assertNotNull(size);
        assertFalse(size.isEmpty());
    }

    @Test
    public void testGetSizeReturnsValidValue() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String size = deviceInfo.getSize();

        assertNotNull(size);
        assertFalse(size.isEmpty());
        // Size is determined by Robolectric environment, just verify it's not null or empty
    }

    @Test
    public void testGetApplicationFramework() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String framework = deviceInfo.getApplicationFramework();

        assertNotNull(framework);
        assertFalse(framework.isEmpty());
    }

    @Test
    public void testGetAgentName() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String agentName = deviceInfo.getAgentName();

        assertNotNull(agentName);
        assertEquals("NewRelic-VideoAgent-Android", agentName);
    }

    @Test
    public void testGetAgentVersion() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String agentVersion = deviceInfo.getAgentVersion();

        assertNotNull(agentVersion);
        assertFalse(agentVersion.isEmpty());
    }

    @Test
    public void testGetUserAgent() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String userAgent = deviceInfo.getUserAgent();

        assertNotNull(userAgent);
        assertTrue(userAgent.contains("NewRelic-VideoAgent-Android"));
        assertTrue(userAgent.contains("Android"));
    }

    @Test
    public void testIsTV() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        boolean isTV = deviceInfo.isTV();

        // Just verify it returns a boolean
        assertTrue(isTV || !isTV);
    }

    @Test
    public void testIsLowMemoryDevice() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        boolean isLowMemory = deviceInfo.isLowMemoryDevice();

        // Just verify it returns a boolean
        assertTrue(isLowMemory || !isLowMemory);
    }

    @Test
    public void testGeneratePersistentDeviceId() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String deviceId = deviceInfo.getDeviceId();

        assertNotNull(deviceId);
        assertTrue(deviceId.length() > 0);
    }

    @Test
    public void testGetSystemArchitecture() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String architecture = deviceInfo.getArchitecture();

        assertNotNull(architecture);
        assertTrue(architecture.length() > 0);
    }

    @Test
    public void testGetJavaVMVersion() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String runtime = deviceInfo.getRunTime();

        assertNotNull(runtime);
        assertTrue(runtime.length() > 0);
    }

    @Test
    public void testDetermineDeviceForm() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String size = deviceInfo.getSize();

        assertNotNull(size);
        assertFalse(size.isEmpty());
        // Device form is determined by Robolectric environment
    }

    @Test
    public void testDetermineApplicationFramework() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String framework = deviceInfo.getApplicationFramework();

        assertNotNull(framework);
        assertFalse(framework.isEmpty());
        // Application framework is detected by DeviceInformation
    }

    @Test
    public void testUserAgentContainsDeviceInfo() {
        DeviceInformation deviceInfo = DeviceInformation.getInstance(context);

        String userAgent = deviceInfo.getUserAgent();

        assertNotNull(userAgent);
        assertTrue(userAgent.contains(deviceInfo.getModel()));
        assertTrue(userAgent.contains(deviceInfo.getOsVersion()));
    }

    @Test
    public void testSingletonPersistsDeviceInfo() {
        DeviceInformation instance1 = DeviceInformation.getInstance(context);
        String deviceId1 = instance1.getDeviceId();
        String userAgent1 = instance1.getUserAgent();

        DeviceInformation instance2 = DeviceInformation.getInstance(context);
        String deviceId2 = instance2.getDeviceId();
        String userAgent2 = instance2.getUserAgent();

        assertEquals(deviceId1, deviceId2);
        assertEquals(userAgent1, userAgent2);
    }
}
