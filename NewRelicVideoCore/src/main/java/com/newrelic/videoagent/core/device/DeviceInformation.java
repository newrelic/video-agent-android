package com.newrelic.videoagent.core.device;

import android.content.Context;
import android.os.Build;
import com.newrelic.videoagent.core.BuildConfig;

import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Device information collector matching AndroidAgentImpl.getDeviceInformation() from New Relic Android Agent
 * Based on the exact fields and method calls from the official New Relic Android Agent repository
 * Initialized once and reused across HTTP requests for optimal performance
 * Thread-safe singleton pattern with lazy initialization
 */
public class DeviceInformation {

    private static final AtomicReference<DeviceInformation> instance = new AtomicReference<>();

    // Core Device Information (exactly matching AndroidAgentImpl.getDeviceInformation())
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
    private final  String userAgent;

    private DeviceInformation(Context context) {

        // Exact same field assignments as AndroidAgentImpl.getDeviceInformation()
        this.osName = "Android";
        this.osVersion = Build.VERSION.RELEASE;
        this.osBuild = Build.VERSION.INCREMENTAL;
        this.model = Build.MODEL;
        this.agentName = "NewRelic-VideoAgent-Android"; // Using our agent name instead of "AndroidAgent"
        this.agentVersion = BuildConfig.VERSION_NAME; // Use version from build.gradle versionName
        this.manufacturer = Build.MANUFACTURER;
        this.deviceId = generatePersistentUUID();
        this.architecture = System.getProperty("os.arch");
        this.runTime = System.getProperty("java.vm.version");
        this.size = determineDeviceForm(context);
        this.applicationFramework = determineApplicationFramework(context); // Detect framework type
        this.applicationFrameworkVersion = determineFrameworkVersion(); // Detect framework version
        this.userAgent = String.format(Locale.US, "%s/%s", agentName, agentVersion);
    }

    /**
     * Generate a persistent UUID similar to AndroidAgentImpl.getUUID()
     * This mimics the behavior of PersistentUUID from the New Relic agent
     */
    private String generatePersistentUUID() {
        try {
            // Use Build.FINGERPRINT as a consistent device identifier
            // This provides a stable identifier across app sessions
            return Build.FINGERPRINT;
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Determine device form factor exactly matching AndroidAgentImpl.deviceForm()
     * Uses Android's Configuration.screenLayout to classify device size
     * Returns lowercase string matching the original implementation
     */
    private String determineDeviceForm(Context context) {
        try {
            DeviceForm deviceForm = getDeviceForm(context);
            return deviceForm.name().toLowerCase(Locale.getDefault());
        } catch (Exception e) {
            // Default to unknown if detection fails
            return DeviceForm.UNKNOWN.name().toLowerCase(Locale.getDefault());
        }
    }

    /**
     * Get device form enum exactly matching AndroidAgentImpl.deviceForm() logic
     * Uses Android's Configuration.screenLayout to classify device size
     */
    private static DeviceForm getDeviceForm(Context context) {
        final int deviceSize = context.getResources().getConfiguration().screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;

        switch (deviceSize) {
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_SMALL:
                return DeviceForm.SMALL;
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL:
                return DeviceForm.NORMAL;
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE:
                return DeviceForm.LARGE;
            default:
                // Android 2.2 doesn't have the XLARGE constant.
                if (deviceSize > android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE) {
                    return DeviceForm.XLARGE;
                } else {
                    return DeviceForm.UNKNOWN;
                }
        }
    }

    /**
     * Get or create device information singleton
     * Thread-safe lazy initialization matching AndroidAgentImpl pattern
     */
    public static DeviceInformation getInstance(Context context) {
        DeviceInformation current = instance.get();
        if (current == null) {
            current = new DeviceInformation(context);
            if (!instance.compareAndSet(null, current)) {
                // Another thread beat us to it, use their instance
                current = instance.get();
            }
        }
        return current;
    }

    /**
     * Determine the application framework type
     * This should detect what framework the app is built with
     */
    private String determineApplicationFramework(Context context) {
        try {
            // Check for React Native
            if (isReactNativeApp(context)) {
                return "React Native";
            }

            // Check for Flutter
            if (isFlutterApp(context)) {
                return "Flutter";
            }

            // Check for Xamarin
            if (isXamarinApp(context)) {
                return "Xamarin";
            }

            // Check for Unity
            if (isUnityApp(context)) {
                return "Unity";
            }

            // Check for Cordova/PhoneGap
            if (isCordovaApp(context)) {
                return "Cordova";
            }

            // Default to Native Android
            return "Native";

        } catch (Exception e) {
            return "Native"; // Default fallback
        }
    }

    /**
     * Check if this is a React Native application
     */
    private boolean isReactNativeApp(Context context) {
        try {
            // React Native apps typically have these classes
            Class.forName("com.facebook.react.ReactApplication");
            return true;
        } catch (ClassNotFoundException e) {
            // Check for React Native in package name or assets
            try {
                String[] assets = context.getAssets().list("");
                for (String asset : assets) {
                    if (asset.equals("index.android.bundle") || asset.equals("index.bundle")) {
                        return true;
                    }
                }
            } catch (Exception ex) {
                // Ignore
            }
        }
        return false;
    }

    /**
     * Check if this is a Flutter application
     */
    private boolean isFlutterApp(Context context) {
        try {
            // Flutter apps have this class
            Class.forName("io.flutter.embedding.android.FlutterActivity");
            return true;
        } catch (ClassNotFoundException e) {
            // Check for Flutter assets
            try {
                String[] assets = context.getAssets().list("flutter_assets");
                return assets != null && assets.length > 0;
            } catch (Exception ex) {
                // Ignore
            }
        }
        return false;
    }

    /**
     * Check if this is a Xamarin application
     */
    private boolean isXamarinApp(Context context) {
        try {
            // Xamarin apps have these classes
            Class.forName("mono.MonoRuntimeProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if this is a Unity application
     */
    private boolean isUnityApp(Context context) {
        try {
            // Unity apps have this class
            Class.forName("com.unity3d.player.UnityPlayer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if this is a Cordova/PhoneGap application
     */
    private boolean isCordovaApp(Context context) {
        try {
            // Cordova apps have this class
            Class.forName("org.apache.cordova.CordovaActivity");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Determine the application framework version
     * This should reflect the version of the framework the app is built with
     */
    private String determineFrameworkVersion() {
        // For native Android apps, this could be the Android Gradle Plugin version,
        // Kotlin version, or Android SDK compile version

        // Option 1: Use Android SDK compile version (most common for native apps)
        return String.valueOf(Build.VERSION.SDK_INT);

        // Option 2: If you want to use a specific framework version, you could add it to BuildConfig:
        // return BuildConfig.FRAMEWORK_VERSION; // Would need to be added to build.gradle

        // Option 3: For React Native apps, this would be the RN version
        // Option 4: For Flutter apps, this would be the Flutter version
        // Option 5: For Xamarin apps, this would be the Xamarin version
    }

    // Getters exactly matching AndroidAgentImpl.java DeviceInformation fields
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

    // Legacy getters for backward compatibility with existing OptimizedHttpClient
    public String getDeviceModel() { return model; }

    public String getUserAgent() {
        return userAgent;
    }
}
