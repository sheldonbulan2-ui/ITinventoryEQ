package com.example.it_inventoryeq.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.it_inventoryeq.models.EquipmentItem;
import com.example.it_inventoryeq.models.ScanLog;
import com.example.it_inventoryeq.models.User;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Thread-safe API and Network Client for Real-Time IT Inventory Synchronization.
 */
public class ApiClient {
    private static final String PREF_NAME = "it_inventory_prefs";
    private static final String KEY_SERVER_URL = "server_base_url";
    private static final String KEY_USER_JSON = "logged_user";
    
    // Live Cloud Production REST API (Render.com + TiDB Cloud Serverless)
    public static final String PRODUCTION_URL = "https://itinventoryeq.onrender.com";
    public static final String DEFAULT_EMULATOR_URL = "http://10.0.2.2:5000";

    private static ApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private ApiClient(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Fast 5-second timeouts with connection pooling for sub-second REST API response
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        // Migrate any legacy emulator/localhost URLs to production Render cloud endpoint
        String saved = prefs.getString(KEY_SERVER_URL, null);
        if (saved == null || saved.trim().isEmpty() || saved.contains("10.0.2.2") || saved.contains("localhost")) {
            prefs.edit().putString(KEY_SERVER_URL, PRODUCTION_URL).apply();
        }
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public String getServerUrl() {
        String saved = prefs.getString(KEY_SERVER_URL, PRODUCTION_URL);
        if (saved == null || saved.trim().isEmpty() || saved.contains("10.0.2.2") || saved.contains("localhost")) {
            return PRODUCTION_URL;
        }
        return saved.trim();
    }

    public void setServerUrl(String url) {
        String cleanUrl = (url == null || url.trim().isEmpty()) ? PRODUCTION_URL : url.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply();
    }

    public void saveUser(User user) {
        prefs.edit().putString(KEY_USER_JSON, gson.toJson(user)).apply();
    }

    public User getSavedUser() {
        String json = prefs.getString(KEY_USER_JSON, null);
        if (json != null) {
            try {
                return gson.fromJson(json, User.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public void logout() {
        prefs.edit().remove(KEY_USER_JSON).apply();
    }

    // =========================================================================
    // API METHODS
    // =========================================================================

    /**
     * Authenticate staff
     */
    public void login(String email, String password, ApiCallback<User> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("password", password);

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(getServerUrl() + "/api/auth/login")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JsonObject resObj = gson.fromJson(responseBody, JsonObject.class);
                    if (response.isSuccessful() && resObj.has("success") && resObj.get("success").getAsBoolean()) {
                        User user = gson.fromJson(resObj.get("user"), User.class);
                        saveUser(user);
                        mainHandler.post(() -> callback.onSuccess(user));
                    } else {
                        String msg = resObj.has("message") ? resObj.get("message").getAsString() : "Login failed";
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Response parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Query equipment item by scanned asset_tag
     */
    public void getEquipmentByTag(String assetTag, ApiCallback<EquipmentItem> callback) {
        Request request = new Request.Builder()
                .url(getServerUrl() + "/api/equipment/tag/" + assetTag.trim())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JsonObject resObj = gson.fromJson(responseBody, JsonObject.class);
                    if (response.isSuccessful() && resObj.has("success") && resObj.get("success").getAsBoolean()) {
                        EquipmentItem item = gson.fromJson(resObj.get("item"), EquipmentItem.class);
                        mainHandler.post(() -> callback.onSuccess(item));
                    } else {
                        // Not found (404) or does not exist
                        mainHandler.post(() -> callback.onSuccess(null));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Atomic scan action: Updates equipment + records scan log + notifies real-time Web Admin Panel
     */
    public void submitScan(JsonObject scanPayload, ApiCallback<JsonObject> callback) {
        RequestBody body = RequestBody.create(scanPayload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(getServerUrl() + "/api/scan")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Failed to sync scan: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JsonObject resObj = gson.fromJson(responseBody, JsonObject.class);
                    if (response.isSuccessful() && resObj.has("success") && resObj.get("success").getAsBoolean()) {
                        mainHandler.post(() -> callback.onSuccess(resObj));
                    } else {
                        String msg = resObj.has("message") ? resObj.get("message").getAsString() : "Scan sync failed";
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Response parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void submitScanLog(JsonObject scanPayload, ApiCallback<JsonObject> callback) {
        submitScan(scanPayload, callback);
    }

    public void getScanLogs(int limit, ApiCallback<List<ScanLog>> callback) {
        getRecentScans(callback);
    }

    /**
     * Fetch recent scan logs for the home feed
     */
    public void getRecentScans(ApiCallback<List<ScanLog>> callback) {
        Request request = new Request.Builder()
                .url(getServerUrl() + "/api/scan-logs?limit=30")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Failed to load scans: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JsonObject resObj = gson.fromJson(responseBody, JsonObject.class);
                    if (response.isSuccessful() && resObj.has("logs")) {
                        JsonArray logsArray = resObj.getAsJsonArray("logs");
                        Type listType = new TypeToken<List<ScanLog>>(){}.getType();
                        List<ScanLog> logs = gson.fromJson(logsArray, listType);
                        mainHandler.post(() -> callback.onSuccess(logs));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Fetch dashboard stats
     */
    public void getStats(ApiCallback<JsonObject> callback) {
        Request request = new Request.Builder()
                .url(getServerUrl() + "/api/stats")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JsonObject resObj = gson.fromJson(responseBody, JsonObject.class);
                    if (response.isSuccessful() && resObj.has("stats")) {
                        mainHandler.post(() -> callback.onSuccess(resObj.getAsJsonObject("stats")));
                    } else {
                        mainHandler.post(() -> callback.onError("Could not fetch stats"));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }
}
