package com.newrelic.videoagent.core.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.newrelic.videoagent.core.BuildConfig;

import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe singleton with comprehensive device detection and caching
 * Zero performance impact after initialization with proper error handling
 */
public final class DeviceInformation {

    private static final String TAG = "NRVideo.DeviceInfo";
    private static final AtomicReference<DeviceInformation> instance = new AtomicReference<>();

    // Immutable device information fields
    private final String osName;
    private final String osVersion;
    private final String osBuild;
    private final String model;
    private final String agentName;
    private final String agentVersion;
    private final String manufacturer;
    private final String deviceId;
    private final String architecture;
    private final String runTime;
    private final String size;
    private final String applicationFramework;
    private final String applicationFrameworkVersion;
    private final String userAgent;
    private final boolean isTV;
    private final boolean isLowMemoryDevice;

    private DeviceInformation(Context context) {
        Context appContext = context.getApplicationContext();

        // Core device information
        this.osName = "Android";
        this.osVersion = Build.VERSION.RELEASE;
        this.osBuild = Build.VERSION.INCREMENTAL;
        this.model = Build.MODEL;
        this.agentName = "NewRelic-VideoAgent-Android"; // Using our agent name instead of "AndroidAgent"
        this.agentVersion = BuildConfig.VERSION_NAME; // Use version from build.gradle versionName
        this.manufacturer = Build.MANUFACTURER;
        this.deviceId = generatePersistentDeviceId();
        this.architecture = getSystemArchitecture();
        this.runTime = getJavaVMVersion();

        // Enhanced device detection
        this.isTV = detectTVPlatform(appContext);
        this.isLowMemoryDevice = detectLowMemoryDevice(appContext);
        this.size = determineDeviceForm(appContext);
        this.applicationFramework = determineApplicationFramework(appContext);
        this.applicationFrameworkVersion = determineFrameworkVersion();
        this.userAgent = createUserAgent();
    }

    /**
     * Thread-safe singleton instance with lazy initialization
     */
    public static DeviceInformation getInstance(Context context) {
        DeviceInformation current = instance.get();
        if (current == null) {
            synchronized (DeviceInformation.class) {
                current = instance.get();
                if (current == null) {
                    try {
                        current = new DeviceInformation(context);
                        instance.set(current);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize DeviceInformation: " + e.getMessage(), e);
                        throw new RuntimeException("DeviceInformation initialization failed", e);
                    }
                }
            }
        }
        return current;
    }

    /**
     * Generate persistent device ID with multiple fallback strategies
     */
    private String generatePersistentDeviceId() {
        try {
            // Use Build.FINGERPRINT for consistency across app sessions
            String fingerprint = Build.FINGERPRINT;
            if (fingerprint != null && !fingerprint.trim().isEmpty()) {
                return fingerprint.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device fingerprint: " + e.getMessage());
        }

        try {
            // Fallback to combination of device identifiers
            String deviceIdentifier = Build.BRAND + "_" + Build.DEVICE + "_" + Build.MODEL;
            return deviceIdentifier.replaceAll("\\s+", "_");
        } catch (Exception e) {
            Log.w(TAG, "Failed to create device identifier: " + e.getMessage());
        }

        // Final fallback to random UUID
        return UUID.randomUUID().toString();
    }

    /**
     * Get system architecture with proper error handling
     */
    private String getSystemArchitecture() {
        try {
            String arch = System.getProperty("os.arch");
            return arch != null ? arch : "unknown";
        } catch (Exception e) {
            Log.w(TAG, "Failed to get system architecture: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Get Java VM version with proper error handling
     */
    private String getJavaVMVersion() {
        try {
            String vmVersion = System.getProperty("java.vm.version");
            return vmVersion != null ? vmVersion : "unknown";
        } catch (Exception e) {
            Log.w(TAG, "Failed to get Java VM version: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Comprehensive TV platform detection with multiple strategies
     */
    private boolean detectTVPlatform(Context context) {
        try {
            PackageManager pm = context.getPackageManager();

            // Primary: Android TV leanback feature
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                return true;
            }

            // Secondary: No touchscreen (common on TV)
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true;
            }

            // Tertiary: UI mode detection
            android.content.res.Configuration config = context.getResources().getConfiguration();
            int uiMode = config.uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK;
            if (uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                return true;
            }

            return false;
        } catch (Exception e) {
            Log.w(TAG, "Failed to detect TV platform: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detect low memory device conditions
     */
    private boolean detectLowMemoryDevice(Context context) {
        try {
            ActivityManager activityManager =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager == null) {
                return false;
            }

            // Check if device is officially low RAM (API 19+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (activityManager.isLowRamDevice()) {
                    return true;
                }
            }

            // Check current memory conditions
            android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            // Consider low memory if less than 512MB available or in low memory state
            return memoryInfo.availMem < 512 * 1024 * 1024 || memoryInfo.lowMemory;

        } catch (Exception e) {
            Log.w(TAG, "Failed to detect low memory device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Determine device form factor with enhanced logic
     */
    private String determineDeviceForm(Context context) {
        try {
            DeviceForm deviceForm = getDeviceForm(context);
            return deviceForm.name().toLowerCase(Locale.US);
        } catch (Exception e) {
            Log.w(TAG, "Failed to determine device form: " + e.getMessage());
            return DeviceForm.UNKNOWN.name().toLowerCase(Locale.US);
        }
    }

    /**
     * Enhanced device form detection
     */
    private DeviceForm getDeviceForm(Context context) {
        try {
            android.content.res.Configuration config = context.getResources().getConfiguration();
            int screenLayout = config.screenLayout;
            int deviceSize = screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;

            // Enhanced detection for TV and tablets
            if (isTV) {
                return DeviceForm.TV;
            }

            switch (deviceSize) {
                case android.content.res.Configuration.SCREENLAYOUT_SIZE_SMALL:
                    return DeviceForm.SMALL;
                case android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL:
                    return DeviceForm.NORMAL;
                case android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE:
                    return DeviceForm.LARGE;
                default:
                    // XLARGE typically indicates tablet
                    if (deviceSize > android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE) {
                        return DeviceForm.TABLET;
                    }
                    return DeviceForm.NORMAL;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device form: " + e.getMessage());
            return DeviceForm.UNKNOWN;
        }
    }

    /**
     * Determine application framework type
     */
    private String determineApplicationFramework(Context context) {
        try {
            // Detect common frameworks
            if (isReactNative()) {
                return "React Native";
            } else if (isFlutter()) {
                return "Flutter";
            } else if (isXamarin()) {
                return "Xamarin";
            } else if (isCordova(context)) {
                return "Cordova";
            } else {
                return "Native Android";
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to determine application framework: " + e.getMessage());
            return "Unknown";
        }
    }

    private boolean isReactNative() {
        try {
            Class.forName("com.facebook.react.ReactApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isFlutter() {
        try {
            Class.forName("io.flutter.embedding.engine.FlutterEngine");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isXamarin() {
        try {
            Class.forName("mono.MonoRuntimeProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isCordova(Context context) {
        try {
            String packageName = context.getPackageName();
            return packageName.contains("cordova") || packageName.contains("phonegap");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determine framework version
     */
    private String determineFrameworkVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * Create optimized user agent string
     */
    private String createUserAgent() {
        try {
            return String.format(Locale.US, "%s/%s (Android %s; %s %s%s)",
                agentName, agentVersion, osVersion, manufacturer, model,
                isTV ? "; TV" : isLowMemoryDevice ? "; LowMem" : "");
        } catch (Exception e) {
            Log.w(TAG, "Failed to create user agent: " + e.getMessage());
            return String.format(Locale.US, "%s/%s", agentName, agentVersion);
        }
    }

    // Immutable getters
    public String getOsName() { return osName; }
    public String getOsVersion() { return osVersion; }
    public String getOsBuild() { return osBuild; }
    public String getModel() { return model; }
    public String getAgentName() { return agentName; }
    public String getAgentVersion() { return agentVersion; }
    public String getManufacturer() { return manufacturer; }
    public String getDeviceId() { return deviceId; }
    public String getArchitecture() { return architecture; }
    public String getRunTime() { return runTime; }
    public String getSize() { return size; }
    public String getApplicationFramework() { return applicationFramework; }
    public String getApplicationFrameworkVersion() { return applicationFrameworkVersion; }
    public String getUserAgent() { return userAgent; }
    public boolean isTV() { return isTV; }
    public boolean isLowMemoryDevice() { return isLowMemoryDevice; }

}
