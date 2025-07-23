# New Relic Video Agent - Customer Onboarding Guide

## Overview
The New Relic Video Agent provides crash-safe video analytics for Android Mobile & TV applications with automatic size management and optimized performance.

## Quick Start Integration

### 1. Add Dependency
```gradle
dependencies {
    implementation 'com.github.newrelic:video-agent-android:3.1.0' // Make sure to use the latest version
}
```

### 2. Initialize in your Video Player Activity/Fragment
```java
public class VideoPlayerActivity extends Activity {
    private ExoPlayer player;
    private NRVideo nrVideo;
    private Integer trackerId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Initialize your ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        
        // 2. Create a New Relic Video configuration
        NRVideoConfiguration config = NRVideoConfiguration.forMobile("YOUR_APPLICATION_TOKEN");
        
        // 3. Set up the New Relic Video agent
        nrVideo = new NRVideo();
        trackerId = nrVideo.setUP(player, getApplicationContext(), config);
        
        // Now the agent will automatically track video events from the player.
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the player
        if (player != null) {
            player.release();
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
    .harvestCycle(60)               // Regular harvest interval
    .liveHarvestCycle(30)           // Live content harvest interval
    .maxBatchSize(8192)             // 8KB batches
    .maxDeadLetterSize(500)         // Failed events queue size
    .enableDebugLogging()           // Debug output
    .memoryOptimized(true)          // Memory optimization
    .build();

// Initialize with custom configuration
nrVideo = new NRVideo();
trackerId = nrVideo.setUP(player, getApplicationContext(), config);
```

### Auto-Optimized Configuration
```java
// Automatically detects device type and applies optimal settings
NRVideoConfiguration config = NRVideoConfiguration.createOptimal("YOUR_TOKEN", getApplicationContext());
nrVideo = new NRVideo();
trackerId = nrVideo.setUP(player, getApplicationContext(), config);
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

// Use the nrVideo instance to record custom events
nrVideo.recordEvent("VIDEO_QUALITY_CHANGE", attributes);
```

### Setting User ID
```java
// Set user ID for all trackers
nrVideo.setUserId("user123");
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
    .enableDebugLogging()
    .build();
```

### Common Issues

1. **High Memory Usage**
   - Check if using TV configuration on mobile device
   - Verify crash safety is working (events should not accumulate)

2. **Missing Events**
   - Ensure you are calling `nrVideo.setUP()` with the correct player instance
   - Check network connectivity during video sessions

3. **Database Growth**
   - Normal: Database should be empty during good connectivity
   - Issue: Persistent database growth indicates network/harvest problems

## Migration from Older Versions

### From Legacy Setup
If you were using deprecated methods, the new setup is simpler.

**OLD (deprecated):**
```java
// These methods don't exist in the current implementation
NRVideo.quickStart("token", context).start();
NRVideo nrVideo = NRVideo.getInstance();
nrVideo.startTracking();
```

**NEW (recommended):**
```java
// In your video player Activity/Fragment
NRVideoConfiguration config = NRVideoConfiguration.forMobile("YOUR_APPLICATION_TOKEN");
NRVideo nrVideo = new NRVideo();
nrVideo.setUP(player, getApplicationContext(), config);
```

The agent provides modern crash-safe analytics optimized for both Android Mobile & TV platforms.
