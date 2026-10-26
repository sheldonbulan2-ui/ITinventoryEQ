package com.example.it_inventoryeq.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.it_inventoryeq.models.EquipmentItem;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Local Database Helper for Offline Inventory Caching & Sync Queue.
 */
public class LocalDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "it_inventory_local.db";
    private static final int DATABASE_VERSION = 1;

    // Table 1: Cached Inventory (for offline lookup)
    public static final String TABLE_CACHED_ITEMS = "cached_inventory";
    public static final String COL_ITEM_ID = "item_id";
    public static final String COL_ASSET_TAG = "asset_tag";
    public static final String COL_ITEM_NAME = "item_name";
    public static final String COL_CATEGORY = "category";
    public static final String COL_SERIAL = "serial_number";
    public static final String COL_STATUS = "status";
    public static final String COL_ASSIGNED_TO = "assigned_to";
    public static final String COL_LOCATION = "location";
    public static final String COL_LAST_SCANNED_AT = "last_scanned_at";
    public static final String COL_LAST_SCANNED_BY = "last_scanned_by";

    // Table 2: Pending Sync Queue (records created while offline)
    public static final String TABLE_SYNC_QUEUE = "pending_sync_queue";
    public static final String COL_QUEUE_ID = "queue_id";
    public static final String COL_ACTION_TYPE = "action_type";
    public static final String COL_NEW_STATUS = "new_status";
    public static final String COL_SCANNED_BY = "scanned_by";
    public static final String COL_REMARKS = "remarks";
    public static final String COL_IS_REGISTRATION = "is_registration";
    public static final String COL_TIMESTAMP = "timestamp";

    private static LocalDatabaseHelper instance;

    public static synchronized LocalDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private LocalDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Cached Inventory Table
        String createCached = "CREATE TABLE " + TABLE_CACHED_ITEMS + " (" +
                COL_ITEM_ID + " TEXT PRIMARY KEY, " +
                COL_ASSET_TAG + " TEXT UNIQUE, " +
                COL_ITEM_NAME + " TEXT, " +
                COL_CATEGORY + " TEXT, " +
                COL_SERIAL + " TEXT, " +
                COL_STATUS + " TEXT, " +
                COL_ASSIGNED_TO + " TEXT, " +
                COL_LOCATION + " TEXT, " +
                COL_LAST_SCANNED_AT + " TEXT, " +
                COL_LAST_SCANNED_BY + " TEXT)";
        db.execSQL(createCached);

        // Pending Sync Queue Table
        String createQueue = "CREATE TABLE " + TABLE_SYNC_QUEUE + " (" +
                COL_QUEUE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ASSET_TAG + " TEXT, " +
                COL_ITEM_NAME + " TEXT, " +
                COL_ACTION_TYPE + " TEXT, " +
                COL_NEW_STATUS + " TEXT, " +
                COL_SCANNED_BY + " TEXT, " +
                COL_LOCATION + " TEXT, " +
                COL_REMARKS + " TEXT, " +
                COL_ASSIGNED_TO + " TEXT, " +
                COL_CATEGORY + " TEXT, " +
                COL_SERIAL + " TEXT, " +
                COL_IS_REGISTRATION + " INTEGER DEFAULT 0, " +
                COL_TIMESTAMP + " TEXT)";
        db.execSQL(createQueue);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CACHED_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNC_QUEUE);
        onCreate(db);
    }

    // =========================================================================
    // INVENTORY CACHING
    // =========================================================================

    public void cacheEquipmentList(List<EquipmentItem> items) {
        if (items == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (EquipmentItem item : items) {
                upsertItemInternal(db, item);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void upsertCachedEquipment(EquipmentItem item) {
        if (item == null) return;
        SQLiteDatabase db = getWritableDatabase();
        upsertItemInternal(db, item);
    }

    private void upsertItemInternal(SQLiteDatabase db, EquipmentItem item) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ITEM_ID, item.getItemId() != null ? item.getItemId() : "EQ-" + System.currentTimeMillis());
        cv.put(COL_ASSET_TAG, item.getAssetTag());
        cv.put(COL_ITEM_NAME, item.getItemName());
        cv.put(COL_CATEGORY, item.getCategory());
        cv.put(COL_SERIAL, item.getSerialNumber());
        cv.put(COL_STATUS, item.getStatus());
        cv.put(COL_ASSIGNED_TO, item.getAssignedTo());
        cv.put(COL_LOCATION, item.getLocation());
        cv.put(COL_LAST_SCANNED_AT, item.getLastScannedAt());
        cv.put(COL_LAST_SCANNED_BY, item.getLastScannedBy());

        db.insertWithOnConflict(TABLE_CACHED_ITEMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public EquipmentItem findEquipmentByTag(String assetTag) {
        if (assetTag == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CACHED_ITEMS, null,
                "LOWER(" + COL_ASSET_TAG + ") = ?",
                new String[]{assetTag.trim().toLowerCase()},
                null, null, null);

        EquipmentItem item = null;
        if (cursor != null && cursor.moveToFirst()) {
            item = cursorToEquipment(cursor);
            cursor.close();
        }
        return item;
    }

    private EquipmentItem cursorToEquipment(Cursor c) {
        return new EquipmentItem(
                c.getString(c.getColumnIndexOrThrow(COL_ITEM_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_ASSET_TAG)),
                c.getString(c.getColumnIndexOrThrow(COL_ITEM_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)),
                c.getString(c.getColumnIndexOrThrow(COL_SERIAL)),
                c.getString(c.getColumnIndexOrThrow(COL_STATUS)),
                c.getString(c.getColumnIndexOrThrow(COL_ASSIGNED_TO)),
                c.getString(c.getColumnIndexOrThrow(COL_LOCATION)),
                c.getString(c.getColumnIndexOrThrow(COL_LAST_SCANNED_AT)),
                c.getString(c.getColumnIndexOrThrow(COL_LAST_SCANNED_BY))
        );
    }

    // =========================================================================
    // OFFLINE SYNC QUEUE
    // =========================================================================

    public long enqueueOfflineScan(JsonObject payload) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();

        String tag = payload.has("asset_tag") ? payload.get("asset_tag").getAsString() : "";
        String itemName = payload.has("item_name") ? payload.get("item_name").getAsString() : "";
        String actionType = payload.has("action_type") ? payload.get("action_type").getAsString() : "Audit Verify";
        String newStatus = payload.has("new_status") ? payload.get("new_status").getAsString() : "Available";
        String scannedBy = payload.has("scanned_by") ? payload.get("scanned_by").getAsString() : "Mobile Scanner";
        String location = payload.has("location") ? payload.get("location").getAsString() : "IT Storage";
        String remarks = payload.has("remarks") ? payload.get("remarks").getAsString() : "";
        String assignedTo = payload.has("assigned_to") ? payload.get("assigned_to").getAsString() : "";
        String category = payload.has("category") ? payload.get("category").getAsString() : "Laptop";
        String serial = payload.has("serial_number") ? payload.get("serial_number").getAsString() : "N/A";
        boolean isReg = "New Registration".equalsIgnoreCase(actionType) || (payload.has("is_registration") && payload.get("is_registration").getAsBoolean());

        cv.put(COL_ASSET_TAG, tag);
        cv.put(COL_ITEM_NAME, itemName);
        cv.put(COL_ACTION_TYPE, actionType);
        cv.put(COL_NEW_STATUS, newStatus);
        cv.put(COL_SCANNED_BY, scannedBy);
        cv.put(COL_LOCATION, location);
        cv.put(COL_REMARKS, remarks);
        cv.put(COL_ASSIGNED_TO, assignedTo);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_SERIAL, serial);
        cv.put(COL_IS_REGISTRATION, isReg ? 1 : 0);
        cv.put(COL_TIMESTAMP, java.time.Instant.now().toString());

        long rowId = db.insert(TABLE_SYNC_QUEUE, null, cv);

        // Also update local cached item immediately so UI sees it
        EquipmentItem cached = findEquipmentByTag(tag);
        if (cached != null) {
            cached.setStatus(newStatus);
            cached.setLocation(location);
            cached.setAssignedTo(assignedTo);
            cached.setLastScannedBy(scannedBy);
            cached.setLastScannedAt(cv.getAsString(COL_TIMESTAMP));
            upsertCachedEquipment(cached);
        } else if (isReg) {
            EquipmentItem newItem = new EquipmentItem("EQ-LOCAL-" + System.currentTimeMillis(), tag, itemName, category, serial, newStatus, assignedTo, location, cv.getAsString(COL_TIMESTAMP), scannedBy);
            upsertCachedEquipment(newItem);
        }

        return rowId;
    }

    public int getPendingScansCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_SYNC_QUEUE, null);
        int count = 0;
        if (c != null && c.moveToFirst()) {
            count = c.getInt(0);
            c.close();
        }
        return count;
    }

    public List<OfflineQueueItem> getPendingScans() {
        List<OfflineQueueItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_SYNC_QUEUE, null, null, null, null, null, COL_QUEUE_ID + " ASC");

        if (c != null && c.moveToFirst()) {
            do {
                OfflineQueueItem item = new OfflineQueueItem();
                item.queueId = c.getLong(c.getColumnIndexOrThrow(COL_QUEUE_ID));
                item.assetTag = c.getString(c.getColumnIndexOrThrow(COL_ASSET_TAG));
                item.itemName = c.getString(c.getColumnIndexOrThrow(COL_ITEM_NAME));
                item.actionType = c.getString(c.getColumnIndexOrThrow(COL_ACTION_TYPE));
                item.newStatus = c.getString(c.getColumnIndexOrThrow(COL_NEW_STATUS));
                item.scannedBy = c.getString(c.getColumnIndexOrThrow(COL_SCANNED_BY));
                item.location = c.getString(c.getColumnIndexOrThrow(COL_LOCATION));
                item.remarks = c.getString(c.getColumnIndexOrThrow(COL_REMARKS));
                item.assignedTo = c.getString(c.getColumnIndexOrThrow(COL_ASSIGNED_TO));
                item.category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY));
                item.serialNumber = c.getString(c.getColumnIndexOrThrow(COL_SERIAL));
                item.isRegistration = c.getInt(c.getColumnIndexOrThrow(COL_IS_REGISTRATION)) == 1;
                item.timestamp = c.getString(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(item);
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public void deletePendingScan(long queueId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SYNC_QUEUE, COL_QUEUE_ID + " = ?", new String[]{String.valueOf(queueId)});
    }

    public static class OfflineQueueItem {
        public long queueId;
        public String assetTag;
        public String itemName;
        public String actionType;
        public String newStatus;
        public String scannedBy;
        public String location;
        public String remarks;
        public String assignedTo;
        public String category;
        public String serialNumber;
        public boolean isRegistration;
        public String timestamp;

        public JsonObject toPayloadJson() {
            JsonObject json = new JsonObject();
            json.addProperty("asset_tag", assetTag);
            json.addProperty("action_type", actionType);
            json.addProperty("new_status", newStatus);
            json.addProperty("scanned_by", scannedBy);
            json.addProperty("location", location);
            json.addProperty("remarks", remarks);
            json.addProperty("assigned_to", assignedTo);
            if (isRegistration || (itemName != null && !itemName.isEmpty())) {
                json.addProperty("item_name", itemName);
                json.addProperty("category", category);
                json.addProperty("serial_number", serialNumber);
            }
            return json;
        }
    }
}
