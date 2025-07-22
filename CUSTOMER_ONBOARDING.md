# New Relic Video Agent - Customer Onboarding Guide

## Overview
The New Relic Video Agent provides crash-safe video analytics for Android Mobile & TV applications with automatic size management and optimized performance.

## Quick Start Integration

### 1. Add Dependency
```gradle
dependencies {
    implementation 'com.github.newrelic:video-agent-android:3.0.3'
}
```

### 2. Initialize in Application Class
```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Quick start for mobile apps
        NRVideo.quickStart("YOUR_APPLICATION_TOKEN", this).start();
        
        // OR for TV apps with optimized settings
        // NRVideo.quickStartTV("YOUR_APPLICATION_TOKEN", this).start();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            NRVideo.appBackgroundedCurrent(); // Emergency backup
        }
    }
}
```

### 3. Video Player Integration
```java
public class VideoPlayerActivity extends Activity {
    private NRVideo nrVideo;
    private Integer trackerId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get the active instance
        nrVideo = NRVideo.getInstance();
        
        // Start video tracking
        trackerId = nrVideo.startTracking();
        
        // Get tracker for video events
        NRVideoTracker tracker = nrVideo.getContentTracker();
        if (tracker != null) {
            tracker.sendStart(); // Video started
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nrVideo != null) {
            nrVideo.stopTracking();
        }
    }
}
```

## Configuration Options

### Mobile-Optimized Setup
```java
NRVideoConfiguration config = NRVideoConfiguration.forMobile("YOUR_TOKEN");
// Features:
// - 120s harvest cycle (battery efficient)
// - 60s live harvest cycle
// - 4KB batch sizes
// - 200 max dead letter queue
// - Memory optimized: true
```

### TV-Optimized Setup  
```java
NRVideoConfiguration config = NRVideoConfiguration.forTV("YOUR_TOKEN");
// Features:
// - 90s harvest cycle
// - 45s live harvest cycle  
// - 16KB batch sizes
// - 1000 max dead letter queue
// - Memory optimized: false
```

### Custom Configuration
```java
NRVideoConfiguration config = new NRVideoConfiguration.Builder("YOUR_TOKEN")
    .region("EU")                    // US or EU
    .harvestCycle(60)               // Regular harvest interval
    .liveHarvestCycle(30)           // Live content harvest interval
    .maxBatchSize(8192)             // 8KB batches
    .maxDeadLetterSize(500)         // Failed events queue size
    .enableDebugLogging(true)       // Debug output
    .memoryOptimized(true)          // Memory optimization
    .build();

NRVideo nrVideo = new NRVideoSetup()
    .withApplicationToken("YOUR_TOKEN")
    .withCrashSafety(true)          // Enable crash-safe storage
    .build(context)
    .start();
```

## Event Types & Usage

### Automatic Events
The agent automatically tracks:
- `CONTENT_START` - Video playback started
- `CONTENT_END` - Video playback ended
- `CONTENT_PAUSE/RESUME` - Playback state changes
- `CONTENT_SEEK` - User seeks in video
- `CONTENT_BUFFER_START/END` - Buffering events
- `CONTENT_ERROR` - Playback errors

### Custom Events
```java
Map<String, Object> attributes = new HashMap<>();
attributes.put("videoTitle", "My Video");
attributes.put("duration", 3600);
attributes.put("quality", "1080p");

nrVideo.recordCustomEvent("VIDEO_QUALITY_CHANGE", attributes);
```

## Memory & Storage Size Analysis

### In-Memory Event Size
Each video event in memory consists of:

```java
// Typical video event structure:
{
    "eventType": "CONTENT_START",           // ~20 bytes
    "timestamp": 1642694400000,             // ~8 bytes  
    "sessionId": "uuid-string",             // ~36 bytes
    "playhead": 0.0,                        // ~8 bytes
    "contentAssetId": "video-123",          // ~20 bytes
    "renditionName": "1080p",               // ~10 bytes
    "isLive": false,                        // ~1 byte
    "custom_attributes": {...}              // Variable size
}
```

**Average Event Size in Memory**: **150-200 bytes per event**

### Memory Buffer Limits

| Device Type | Live Events Buffer | On-Demand Buffer | Dead Letter Queue | Total Memory |
|-------------|-------------------|------------------|-------------------|--------------|
| **Mobile**  | 500 events (75KB) | 1000 events (150KB) | 200 events (30KB) | **~255KB** |
| **TV**      | 1000 events (150KB) | 2000 events (300KB) | 1000 events (150KB) | **~600KB** |

### SQLite Database Storage

**Database Usage Pattern**:
- **Normal Operation**: Database remains **empty** (0 bytes)
- **Crash/Network Failure**: Events backed up to SQLite
- **Recovery**: Database gradually empties as events are sent

**Database Size Calculations**:

| Scenario | Events Count | JSON Size per Event | Total DB Size |
|----------|--------------|-------------------|---------------|
| **30-min session backup** | ~100 events | ~180 bytes | **~18KB** |
| **2-hour session backup** | ~400 events | ~180 bytes | **~72KB** |
| **Maximum mobile buffer** | 1,700 events | ~180 bytes | **~306KB** |
| **Maximum TV buffer** | 4,000 events | ~180 bytes | **~720KB** |

### SQLite Limits & Android Considerations

**SQLite Maximum Limits**:
- **Default page size**: 4KB
- **Maximum database size**: 281TB (theoretical)
- **Practical Android limit**: Available device storage

**Android Storage Quotas**:
- **Mobile devices**: Typically 100MB+ available for app databases
- **TV devices**: Typically 500MB+ available for app databases
- **Auto-cleanup**: Events older than 7 days automatically removed

**Storage Efficiency**:
```sql
-- Optimized storage schema
CREATE TABLE backup_events (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,  -- 8 bytes
    event_data TEXT NOT NULL,               -- ~180 bytes (JSON)
    priority TEXT NOT NULL,                 -- ~10 bytes
    timestamp INTEGER NOT NULL              -- 8 bytes
);
-- Total per row: ~206 bytes + SQLite overhead (~240 bytes per event)
```

## Performance Impact

### CPU Usage
- **Normal operation**: <1% CPU (events stay in memory)
- **Crash recovery**: 2-3% CPU during recovery phase
- **Background harvest**: <0.5% CPU every 60-90 seconds

### Memory Usage
- **Baseline**: ~50KB (agent components)
- **Active buffering**: 255KB (mobile) / 600KB (TV)
- **Peak usage**: <1MB total

### Network Usage
- **Regular batches**: 4KB-16KB per harvest
- **Frequency**: Every 60-120 seconds
- **Daily estimate**: 2-5MB for typical video app usage

### Battery Impact
- **Mobile optimization**: Longer harvest cycles (120s)
- **Background processing**: Minimal (uses Android Handler, not threads)
- **Crash safety**: Zero overhead during normal operation

## Device-Specific Optimizations

### Mobile Phones/Tablets
```java
// Automatically applied in forMobile() configuration
- Harvest cycle: 120 seconds (battery efficient)
- Batch size: 4KB (network efficient)  
- Memory limit: 255KB total
- Crash safety: Enabled
- Background optimization: Aggressive
```

### Android TV
```java
// Automatically applied in forTV() configuration  
- Harvest cycle: 90 seconds (performance optimized)
- Batch size: 16KB (throughput optimized)
- Memory limit: 600KB total
- Crash safety: Enabled
- Background optimization: Relaxed
```

### Auto-Detection
The agent automatically detects device type:
```java
// TV detection via PackageManager
boolean isTV = context.getPackageManager()
    .hasSystemFeature("android.software.leanback");
```

## Troubleshooting

### Debug Logging
```java
NRVideoConfiguration config = new NRVideoConfiguration.Builder("YOUR_TOKEN")
    .enableDebugLogging(true)
    .build();
```

### Recovery Statistics
```java
NRVideo nrVideo = NRVideo.getInstance();
System.out.println("Usage stats: " + nrVideo.getUsageStats());
System.out.println("Is recovering: " + nrVideo.isRecovering());
```

### Common Issues

1. **High Memory Usage**
   - Check if using TV configuration on mobile device
   - Verify crash safety is working (events should not accumulate)

2. **Missing Events**
   - Ensure app lifecycle callbacks are implemented
   - Check network connectivity during video sessions

3. **Database Growth**
   - Normal: Database should be empty during good connectivity
   - Issue: Persistent database growth indicates network/harvest problems

## Migration from Older Versions

### From Legacy Setup
```java
// OLD (deprecated)
NRVideo video = new NRVideoSetup()
    .withLicenseKey("token")
    .build(); // No crash safety

// NEW (recommended)  
NRVideo video = NRVideo.quickStart("token", context).start();
```

### Gradual Rollout
1. **Phase 1**: Deploy with crash safety disabled for testing
2. **Phase 2**: Enable crash safety for subset of users
3. **Phase 3**: Full rollout with crash safety enabled

The agent provides backward compatibility while offering modern crash-safe analytics optimized for both Android Mobile & TV platforms.
