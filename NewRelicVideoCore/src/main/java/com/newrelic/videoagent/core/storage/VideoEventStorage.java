package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Optimized SQLite storage for crash recovery and failed event backup
 * Designed for minimal overhead - only used during failures/crashes
 * No longer a singleton - managed by CrashSafeHarvestFactory
 */
public class VideoEventStorage extends SQLiteOpenHelper {

    private static final String DB_NAME = "nr_video_backup.db";
    private static final int DB_VERSION = 1;

    // Single table for all backup events
    private static final String TABLE_BACKUP = "backup_events";
    private static final String COL_ID = "_id";
    private static final String COL_DATA = "event_data";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_TIMESTAMP = "timestamp";

    public VideoEventStorage(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BACKUP + " (" +
                   COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COL_DATA + " TEXT NOT NULL, " +
                   COL_PRIORITY + " TEXT NOT NULL, " +
                   COL_TIMESTAMP + " INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_priority_time ON " + TABLE_BACKUP +
                   "(" + COL_PRIORITY + ", " + COL_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BACKUP);
        onCreate(db);
    }

    /**
     * Backup events from memory during emergency/crash
     */
    public void backupEvents(List<Map<String, Object>> liveEvents,
                           List<Map<String, Object>> ondemandEvents) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map<String, Object> event : liveEvents) {
                insertEvent(db, event, "live");
            }
            for (Map<String, Object> event : ondemandEvents) {
                insertEvent(db, event, "ondemand");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Backup failed events when retries exhausted
     */
    public void backupFailedEvents(List<Map<String, Object>> failedEvents) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map<String, Object> event : failedEvents) {
                insertEvent(db, event, "failed");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Poll events for recovery in small batches
     */
    public List<Map<String, Object>> pollEvents(String priority, int maxCount) {
        List<Map<String, Object>> events = new ArrayList<>();
        SQLiteDatabase db = getWritableDatabase();

        // Get events to recover
        String query = "SELECT " + COL_ID + ", " + COL_DATA + " FROM " + TABLE_BACKUP +
                      " WHERE " + COL_PRIORITY + " = ? ORDER BY " + COL_TIMESTAMP + " LIMIT ?";

        Cursor cursor = db.rawQuery(query, new String[]{priority, String.valueOf(maxCount)});
        List<Long> idsToRemove = new ArrayList<>();

        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String data = cursor.getString(1);

                Map<String, Object> event = jsonToMap(data);
                if (event != null) {
                    events.add(event);
                    idsToRemove.add(id);
                }
            }
        } finally {
            cursor.close();
        }

        // Remove recovered events
        if (!idsToRemove.isEmpty()) {
            db.beginTransaction();
            try {
                for (Long id : idsToRemove) {
                    db.delete(TABLE_BACKUP, COL_ID + " = ?", new String[]{String.valueOf(id)});
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        return events;
    }

    public int getEventCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_BACKUP, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public boolean hasBackupData() {
        return getEventCount() > 0;
    }

    public boolean isEmpty() {
        return getEventCount() == 0;
    }

    public void cleanup() {
        // Remove events older than 7 days
        long cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_BACKUP, COL_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoff)});
    }

    // Helper methods
    private void insertEvent(SQLiteDatabase db, Map<String, Object> event, String priority) {
        String json = mapToJson(event);
        long timestamp = System.currentTimeMillis();

        db.execSQL("INSERT INTO " + TABLE_BACKUP + " (" + COL_DATA + ", " + COL_PRIORITY + ", " + COL_TIMESTAMP +
                   ") VALUES (?, ?, ?)", new Object[]{json, priority, timestamp});
    }

    private String mapToJson(Map<String, Object> map) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> jsonToMap(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            Map<String, Object> map = new HashMap<>();

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, json.get(key));
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }
}
