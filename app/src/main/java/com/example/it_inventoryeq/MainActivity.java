package com.example.it_inventoryeq;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.it_inventoryeq.adapters.OfflineQueueAdapter;
import com.example.it_inventoryeq.adapters.ScanLogAdapter;
import com.example.it_inventoryeq.database.LocalDatabaseHelper;
import com.example.it_inventoryeq.models.EquipmentItem;
import com.example.it_inventoryeq.models.ScanLog;
import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.example.it_inventoryeq.network.OfflineSyncManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Main Home Dashboard with Bottom Navigation Bar, Manual Tag Entry, and Offline-First Syncing.
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvStaffGreeting;
    private TextView tvStaffRole;
    private TextView tvScannedTodayCount;
    private TextView tvLiveSyncStatus;
    private MaterialButton btnStartScan;
    private MaterialButton btnQuickManualInput;
    private SwipeRefreshLayout swipeRefreshScans;
    private RecyclerView rvRecentScans;
    private LinearLayout layoutEmptyScans;
    private ScanLogAdapter scanLogAdapter;

    // Tabs & Navigation
    private BottomNavigationView bottomNavigation;
    private LinearLayout layoutHomeTab;
    private View layoutManualTab;
    private LinearLayout layoutQueueTab;

    // Manual Input Tab Views
    private EditText etManualInputTag;
    private MaterialButton btnLookupManualTag;

    // Offline Queue Tab Views
    private RecyclerView rvOfflineQueue;
    private LinearLayout layoutEmptyQueue;
    private TextView tvQueueCountBadge;
    private MaterialButton btnSyncQueueNow;
    private ProgressBar queueSyncProgressBar;
    private OfflineQueueAdapter offlineQueueAdapter;

    private ApiClient apiClient;
    private LocalDatabaseHelper localDb;
    private OfflineSyncManager syncManager;
    private User currentUser;

    private final ActivityResultLauncher<Intent> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String assetTag = result.getData().getStringExtra("scanned_asset_tag");
                    boolean isOffline = result.getData().getBooleanExtra("is_offline", false);
                    if (assetTag != null) {
                        Toast.makeText(this, isOffline ? "Queued offline: " + assetTag : "Synced tag: " + assetTag, Toast.LENGTH_SHORT).show();
                    }
                    refreshAllTabs();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClient.getInstance(this);
        localDb = LocalDatabaseHelper.getInstance(this);
        syncManager = OfflineSyncManager.getInstance();

        currentUser = apiClient.getSavedUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Bind Common Header
        tvStaffGreeting = findViewById(R.id.tvStaffGreeting);
        tvStaffRole = findViewById(R.id.tvStaffRole);
        tvScannedTodayCount = findViewById(R.id.tvScannedTodayCount);
        tvLiveSyncStatus = findViewById(R.id.tvLiveSyncStatus);

        tvStaffGreeting.setText("Welcome, " + currentUser.getFullName());
        tvStaffRole.setText((currentUser.getRole() != null ? currentUser.getRole().toUpperCase() : "SCANNER") + " CONSOLE");

        // Bind Tabs & Containers
        layoutHomeTab = findViewById(R.id.layoutHomeTab);
        layoutManualTab = findViewById(R.id.layoutManualTab);
        layoutQueueTab = findViewById(R.id.layoutQueueTab);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Bind Home Tab Views
        btnStartScan = findViewById(R.id.btnStartScan);
        btnQuickManualInput = findViewById(R.id.btnQuickManualInput);
        swipeRefreshScans = findViewById(R.id.swipeRefreshScans);
        rvRecentScans = findViewById(R.id.rvRecentScans);
        layoutEmptyScans = findViewById(R.id.layoutEmptyScans);

        scanLogAdapter = new ScanLogAdapter(this);
        rvRecentScans.setLayoutManager(new LinearLayoutManager(this));
        rvRecentScans.setAdapter(scanLogAdapter);

        swipeRefreshScans.setColorSchemeResources(R.color.primary, R.color.accent);
        swipeRefreshScans.setOnRefreshListener(this::loadDashboardData);

        // Bind Manual Tab Views
        etManualInputTag = findViewById(R.id.etManualInputTag);
        btnLookupManualTag = findViewById(R.id.btnLookupManualTag);

        // Bind Offline Queue Tab Views
        rvOfflineQueue = findViewById(R.id.rvOfflineQueue);
        layoutEmptyQueue = findViewById(R.id.layoutEmptyQueue);
        tvQueueCountBadge = findViewById(R.id.tvQueueCountBadge);
        btnSyncQueueNow = findViewById(R.id.btnSyncQueueNow);
        queueSyncProgressBar = findViewById(R.id.queueSyncProgressBar);

        offlineQueueAdapter = new OfflineQueueAdapter(this);
        rvOfflineQueue.setLayoutManager(new LinearLayoutManager(this));
        rvOfflineQueue.setAdapter(offlineQueueAdapter);

        // Setup Bottom Navigation Bar
        setupBottomNavigation();

        // Setup Button Listeners
        setupActionListeners();

        // Initial Data Fetch & Offline Sync Check
        refreshAllTabs();
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchTab(0);
                return true;
            } else if (id == R.id.nav_scanner) {
                // Directly launch camera scanner
                Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
                scannerLauncher.launch(intent);
                return true;
            } else if (id == R.id.nav_manual) {
                switchTab(1);
                return true;
            } else if (id == R.id.nav_queue) {
                switchTab(2);
                return true;
            }
            return false;
        });
    }

    private void switchTab(int tabIndex) {
        layoutHomeTab.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutManualTab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutQueueTab.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        if (tabIndex == 0) {
            loadDashboardData();
        } else if (tabIndex == 2) {
            refreshOfflineQueue();
        }
    }

    private void setupActionListeners() {
        // Camera scan action
        btnStartScan.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        // Quick button to jump to manual tab
        btnQuickManualInput.setOnClickListener(v -> {
            bottomNavigation.setSelectedItemId(R.id.nav_manual);
        });

        // Manual Input lookup button
        btnLookupManualTag.setOnClickListener(v -> {
            String tag = etManualInputTag.getText().toString().trim();
            if (tag.isEmpty()) {
                etManualInputTag.setError("Please enter an asset tag or serial");
                return;
            }
            performManualTagAudit(tag);
        });

        // Sample tag buttons in manual tab
        findViewById(R.id.btnTagSample1).setOnClickListener(v -> {
            etManualInputTag.setText("IT-LAP-001");
            performManualTagAudit("IT-LAP-001");
        });
        findViewById(R.id.btnTagSample2).setOnClickListener(v -> {
            etManualInputTag.setText("IT-MON-001");
            performManualTagAudit("IT-MON-001");
        });

        // Sync Now button in offline queue tab
        btnSyncQueueNow.setOnClickListener(v -> {
            triggerManualSync();
        });

        // Settings Button
        findViewById(R.id.btnMainSettings).setOnClickListener(v -> showServerSettingsDialog());

        // Logout Button
        findViewById(R.id.btnMainLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to log out of the scanner app?")
                    .setPositiveButton("Logout", (d, w) -> {
                        apiClient.logout();
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllTabs();
    }

    private void refreshAllTabs() {
        updateConnectivityBanner();
        loadDashboardData();
        refreshOfflineQueue();
        // Also cache inventory for offline usage
        syncManager.cacheAllEquipment(this, null);

        // Auto-flush pending queue if online
        if (syncManager.isOnline(this) && localDb.getPendingScansCount() > 0) {
            triggerManualSync();
        }
    }

    private void updateConnectivityBanner() {
        int pending = localDb.getPendingScansCount();
        if (syncManager.isOnline(this)) {
            if (pending > 0) {
                tvLiveSyncStatus.setText("● Online (" + pending + " pending sync)");
            } else {
                tvLiveSyncStatus.setText("● Online & Synchronized");
            }
        } else {
            tvLiveSyncStatus.setText("○ Offline Mode (" + pending + " queued in SQLite)");
        }
    }

    private void loadDashboardData() {
        swipeRefreshScans.setRefreshing(true);

        // Fetch Stats
        apiClient.getStats(new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject stats) {
                if (stats.has("scanned_today")) {
                    tvScannedTodayCount.setText(String.valueOf(stats.get("scanned_today").getAsInt()));
                }
                updateConnectivityBanner();
            }

            @Override
            public void onError(String message) {
                updateConnectivityBanner();
            }
        });

        // Fetch Recent Scans
        apiClient.getRecentScans(new ApiClient.ApiCallback<List<ScanLog>>() {
            @Override
            public void onSuccess(List<ScanLog> logs) {
                swipeRefreshScans.setRefreshing(false);
                scanLogAdapter.setScanLogs(logs);

                if (logs.isEmpty()) {
                    layoutEmptyScans.setVisibility(View.VISIBLE);
                    rvRecentScans.setVisibility(View.GONE);
                } else {
                    layoutEmptyScans.setVisibility(View.GONE);
                    rvRecentScans.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                swipeRefreshScans.setRefreshing(false);
            }
        });
    }

    private void refreshOfflineQueue() {
        List<LocalDatabaseHelper.OfflineQueueItem> pending = localDb.getPendingScans();
        int count = pending.size();

        tvQueueCountBadge.setText(count + (count == 1 ? " ITEM" : " ITEMS"));
        offlineQueueAdapter.setItems(pending);

        if (count == 0) {
            layoutEmptyQueue.setVisibility(View.VISIBLE);
            rvOfflineQueue.setVisibility(View.GONE);
            btnSyncQueueNow.setEnabled(false);
            btnSyncQueueNow.setText("All Queued Items Synced");
        } else {
            layoutEmptyQueue.setVisibility(View.GONE);
            rvOfflineQueue.setVisibility(View.VISIBLE);
            btnSyncQueueNow.setEnabled(true);
            btnSyncQueueNow.setText("Sync " + count + " Pending Item(s) Now");
        }

        updateConnectivityBanner();
    }

    private void triggerManualSync() {
        if (!syncManager.isOnline(this)) {
            Toast.makeText(this, "Device is offline. Reconnect to Wi-Fi to sync with database.", Toast.LENGTH_SHORT).show();
            return;
        }

        queueSyncProgressBar.setVisibility(View.VISIBLE);
        btnSyncQueueNow.setEnabled(false);

        syncManager.flushPendingQueue(this, (synced, failed) -> {
            queueSyncProgressBar.setVisibility(View.GONE);
            btnSyncQueueNow.setEnabled(true);

            if (synced > 0) {
                Toast.makeText(this, "Successfully synced " + synced + " item(s) to live database!", Toast.LENGTH_SHORT).show();
            }
            if (failed > 0) {
                Toast.makeText(this, failed + " item(s) could not sync. Will retry.", Toast.LENGTH_SHORT).show();
            }

            refreshOfflineQueue();
            loadDashboardData();
        });
    }

    /**
     * Manual Asset Tag Audit & Lookup
     */
    private void performManualTagAudit(String assetTag) {
        // Query local cache first or server
        EquipmentItem cached = localDb.findEquipmentByTag(assetTag);

        if (!syncManager.isOnline(this)) {
            if (cached != null) {
                showActionDialog(cached, true);
            } else {
                showRegisterDialog(assetTag, true);
            }
            return;
        }

        // Online lookup
        apiClient.getEquipmentByTag(assetTag, new ApiClient.ApiCallback<EquipmentItem>() {
            @Override
            public void onSuccess(EquipmentItem item) {
                if (item != null) {
                    localDb.upsertCachedEquipment(item);
                    showActionDialog(item, false);
                } else if (cached != null) {
                    showActionDialog(cached, false);
                } else {
                    showRegisterDialog(assetTag, false);
                }
            }

            @Override
            public void onError(String message) {
                if (cached != null) {
                    showActionDialog(cached, true);
                } else {
                    showRegisterDialog(assetTag, true);
                }
            }
        });
    }

    private void showActionDialog(EquipmentItem item, boolean isOffline) {
        View view = getLayoutInflater().inflate(R.layout.dialog_item_action, null);

        TextView tvAssetTag = view.findViewById(R.id.dialogAssetTag);
        TextView tvCurrentStatus = view.findViewById(R.id.dialogCurrentStatus);
        TextView tvItemName = view.findViewById(R.id.dialogItemName);
        TextView tvSerial = view.findViewById(R.id.dialogSerial);
        TextView tvAssigned = view.findViewById(R.id.dialogAssigned);
        Spinner spinnerAction = view.findViewById(R.id.spinnerActionType);
        EditText etAssignedTo = view.findViewById(R.id.etDialogAssignedTo);
        EditText etLocation = view.findViewById(R.id.etDialogLocation);
        EditText etRemarks = view.findViewById(R.id.etDialogRemarks);
        MaterialButton btnCancel = view.findViewById(R.id.btnDialogCancel);
        MaterialButton btnSaveSync = view.findViewById(R.id.btnDialogSaveSync);

        tvAssetTag.setText(item.getAssetTag());
        tvItemName.setText(item.getItemName());
        tvCurrentStatus.setText(item.getStatus().toUpperCase());
        tvSerial.setText("S/N: " + item.getSerialNumber() + " | Cat: " + item.getCategory());
        tvAssigned.setText(item.getAssignedTo() != null && !item.getAssignedTo().isEmpty()
                ? "Assigned to: " + item.getAssignedTo()
                : "Currently: Unassigned / Storage");

        etAssignedTo.setText(item.getAssignedTo());
        etLocation.setText(item.getLocation());

        if (isOffline) {
            btnSaveSync.setText("Save to Offline Queue");
        }

        String[] actions = new String[]{
                "Audit Verify (Working / Available)",
                "Check-Out / Assign (In Use)",
                "Send to Maintenance",
                "Report Defective / Non-working"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actions);
        spinnerAction.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSaveSync.setOnClickListener(v -> {
            int selectedPos = spinnerAction.getSelectedItemPosition();
            String actionType = "Audit Verify";
            String newStatus = "Available";

            if (selectedPos == 1) {
                actionType = "Check-Out / Assign";
                newStatus = "Assigned";
            } else if (selectedPos == 2) {
                actionType = "Send to Maintenance";
                newStatus = "Maintenance";
            } else if (selectedPos == 3) {
                actionType = "Report Defective";
                newStatus = "Defective";
            }

            String updatedAssigned = etAssignedTo.getText().toString().trim();
            String updatedLocation = etLocation.getText().toString().trim();
            String remarks = etRemarks.getText().toString().trim();

            JsonObject payload = new JsonObject();
            payload.addProperty("asset_tag", item.getAssetTag());
            payload.addProperty("item_name", item.getItemName());
            payload.addProperty("category", item.getCategory());
            payload.addProperty("serial_number", item.getSerialNumber());
            payload.addProperty("action_type", actionType);
            payload.addProperty("new_status", newStatus);
            payload.addProperty("scanned_by", currentUser != null ? currentUser.getFullName() : "Mobile Scanner");
            payload.addProperty("location", updatedLocation.isEmpty() ? item.getLocation() : updatedLocation);
            payload.addProperty("assigned_to", updatedAssigned);
            payload.addProperty("remarks", remarks);

            syncManager.submitScanOrQueue(MainActivity.this, payload, new OfflineSyncManager.ScanActionCallback() {
                @Override
                public void onSuccess(boolean isOfflineQueued, String message) {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    refreshAllTabs();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void showRegisterDialog(String assetTag, boolean isOffline) {
        View view = getLayoutInflater().inflate(R.layout.dialog_register_item, null);

        TextView tvAssetTag = view.findViewById(R.id.tvNewAssetTag);
        EditText etItemName = view.findViewById(R.id.etNewItemName);
        Spinner spinnerCategory = view.findViewById(R.id.spinnerNewCategory);
        Spinner spinnerStatus = view.findViewById(R.id.spinnerNewStatus);
        EditText etSerial = view.findViewById(R.id.etNewSerial);
        EditText etLocation = view.findViewById(R.id.etNewLocation);
        EditText etAssignedTo = view.findViewById(R.id.etNewAssignedTo);
        MaterialButton btnCancel = view.findViewById(R.id.btnNewCancel);
        MaterialButton btnRegisterSync = view.findViewById(R.id.btnNewRegisterSync);

        tvAssetTag.setText(assetTag);

        if (isOffline) {
            btnRegisterSync.setText("Queue Registration (Offline)");
        }

        String[] categories = new String[]{"Laptop", "Desktop", "Monitor", "Network", "Peripheral"};
        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));

        String[] statuses = new String[]{"Available", "Assigned", "Maintenance", "Defective"};
        spinnerStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses));

        etLocation.setText("IT Storage Room");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnRegisterSync.setOnClickListener(v -> {
            String name = etItemName.getText().toString().trim();
            if (name.isEmpty()) {
                etItemName.setError("Item name is required");
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("asset_tag", assetTag);
            payload.addProperty("item_name", name);
            payload.addProperty("category", (String) spinnerCategory.getSelectedItem());
            payload.addProperty("serial_number", etSerial.getText().toString().trim().isEmpty() ? "N/A" : etSerial.getText().toString().trim());
            payload.addProperty("new_status", (String) spinnerStatus.getSelectedItem());
            payload.addProperty("location", etLocation.getText().toString().trim().isEmpty() ? "IT Storage" : etLocation.getText().toString().trim());
            payload.addProperty("assigned_to", etAssignedTo.getText().toString().trim());
            payload.addProperty("action_type", "New Registration");
            payload.addProperty("is_registration", true);
            payload.addProperty("remarks", "Direct manual entry from mobile app");
            payload.addProperty("scanned_by", currentUser != null ? currentUser.getFullName() : "Mobile Scanner");

            syncManager.submitScanOrQueue(MainActivity.this, payload, new OfflineSyncManager.ScanActionCallback() {
                @Override
                public void onSuccess(boolean isOfflineQueued, String message) {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    refreshAllTabs();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Registration Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void showServerSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        EditText etUrl = dialogView.findViewById(R.id.etServerUrlSetting);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveServerSetting);
        MaterialButton btnReset = dialogView.findViewById(R.id.btnResetDefaultUrl);

        etUrl.setText(apiClient.getServerUrl());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnReset.setOnClickListener(v -> etUrl.setText(ApiClient.DEFAULT_EMULATOR_URL));

        btnSave.setOnClickListener(v -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                apiClient.setServerUrl(newUrl);
                Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshAllTabs();
            }
        });

        dialog.show();
    }
}
