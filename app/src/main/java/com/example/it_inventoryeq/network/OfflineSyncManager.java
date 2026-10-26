package com.example.it_inventoryeq.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.example.it_inventoryeq.database.LocalDatabaseHelper;
import com.example.it_inventoryeq.models.EquipmentItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Intelligent Offline-First Sync Engine.
 * Automatically saves scans to local SQLite when offline and flushes to server when reconnected.
 */
public class OfflineSyncManager {

    private static OfflineSyncManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    public interface SyncResultCallback {
        void onComplete(int syncedCount, int failedCount);
    }

    public interface ScanActionCallback {
        void onSuccess(boolean isOfflineQueued, String message);
        void onError(String error);
    }

    public static synchronized OfflineSyncManager getInstance() {
        if (instance == null) {
            instance = new OfflineSyncManager();
        }
        return instance;
    }

    private OfflineSyncManager() {}

    /**
     * Check if device has an active network connection
     */
    public boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /**
     * Submit scan online or immediately queue offline
     */
    public void submitScanOrQueue(Context context, JsonObject payload, ScanActionCallback callback) {
        LocalDatabaseHelper db = LocalDatabaseHelper.getInstance(context);
        ApiClient apiClient = ApiClient.getInstance(context);

        if (!isOnline(context)) {
            // Immediately store to local SQLite
            db.enqueueOfflineScan(payload);
            mainHandler.post(() -> callback.onSuccess(true, "Saved to Offline Queue (No internet connection)"));
            return;
        }

        // Attempt online sync
        apiClient.submitScan(payload, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                // Online sync succeeded
                mainHandler.post(() -> callback.onSuccess(false, "Synchronized to live database & Web Admin"));
            }

            @Override
            public void onError(String message) {
                // Network failed mid-request -> fallback to local offline queue
                db.enqueueOfflineScan(payload);
                mainHandler.post(() -> callback.onSuccess(true, "Network timeout: Queued locally. Will auto-sync when connected"));
            }
        });
    }

    /**
     * Flush all pending offline records to the backend database
     */
    public void flushPendingQueue(Context context, SyncResultCallback callback) {
        if (!isOnline(context)) {
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(0, LocalDatabaseHelper.getInstance(context).getPendingScansCount()));
            }
            return;
        }

        if (isSyncing.getAndSet(true)) {
            return; // Already actively flushing
        }

        new Thread(() -> {
            LocalDatabaseHelper db = LocalDatabaseHelper.getInstance(context);
            ApiClient apiClient = ApiClient.getInstance(context);
            List<LocalDatabaseHelper.OfflineQueueItem> pending = db.getPendingScans();

            int synced = 0;
            int failed = 0;

            for (LocalDatabaseHelper.OfflineQueueItem item : pending) {
                try {
                    JsonObject payload = item.toPayloadJson();
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(
                            payload.toString(),
                            okhttp3.MediaType.get("application/json; charset=utf-8")
                    );

                    Request request = new Request.Builder()
                            .url(apiClient.getServerUrl() + "/api/scan")
                            .post(body)
                            .build();

                    // Synchronous call on background thread
                    okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .build();

                    Response response = httpClient.newCall(request).execute();
                    if (response.isSuccessful()) {
                        db.deletePendingScan(item.queueId);
                        synced++;
                    } else {
                        failed++;
                    }
                    response.close();
                } catch (Exception e) {
                    failed++;
                }
            }

            isSyncing.set(false);
            final int finalSynced = synced;
            final int finalFailed = failed;
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(finalSynced, finalFailed));
            }
        }).start();
    }

    /**
     * Cache all equipment items locally for offline lookup
     */
    public void cacheAllEquipment(Context context, Runnable onFinish) {
        if (!isOnline(context)) {
            if (onFinish != null) onFinish.run();
            return;
        }

        ApiClient apiClient = ApiClient.getInstance(context);
        Request request = new Request.Builder()
                .url(apiClient.getServerUrl() + "/api/equipment")
                .get()
                .build();

        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (onFinish != null) mainHandler.post(onFinish);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonStr = response.body().string();
                        JsonObject obj = new Gson().fromJson(jsonStr, JsonObject.class);
                        if (obj.has("items")) {
                            JsonArray itemsArray = obj.getAsJsonArray("items");
                            Type listType = new TypeToken<List<EquipmentItem>>(){}.getType();
                            List<EquipmentItem> items = new Gson().fromJson(itemsArray, listType);
                            LocalDatabaseHelper.getInstance(context).cacheEquipmentList(items);
                        }
                    } catch (Exception ignored) {}
                }
                if (onFinish != null) mainHandler.post(onFinish);
            }
        });
    }
}
