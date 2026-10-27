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

import com.example.it_inventoryeq.adapters.ScanLogAdapter;
import com.example.it_inventoryeq.database.LocalDatabaseHelper;
import com.example.it_inventoryeq.models.EquipmentItem;
import com.example.it_inventoryeq.models.ScanLog;
import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Main Home Dashboard with Fast Continuous Scanning & Manual Tag Input.
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

    // Manual Input Tab Views
    private EditText etManualInputTag;
    private MaterialButton btnLookupManualTag;

    private ApiClient apiClient;
    private LocalDatabaseHelper localDb;
    private User currentUser;

    private final ActivityResultLauncher<Intent> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                loadDashboardData();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClient.getInstance(this);
        localDb = LocalDatabaseHelper.getInstance(this);

        currentUser = apiClient.getSavedUser();
        if (currentUser == null) {
            currentUser = new User("usr_staff", "IT Field Staff", "staff@itinventory.local", "scanner");
            apiClient.saveUser(currentUser);
        }

        setContentView(R.layout.activity_main);

        // Bind Common Header
        tvStaffGreeting = findViewById(R.id.tvStaffGreeting);
        tvStaffRole = findViewById(R.id.tvStaffRole);
        tvScannedTodayCount = findViewById(R.id.tvScannedTodayCount);
        tvLiveSyncStatus = findViewById(R.id.tvLiveSyncStatus);

        tvStaffGreeting.setText("Welcome, " + currentUser.getFullName());
        tvStaffRole.setText("HARDWARE SCANNER & AUDIT");

        // Bind Tabs & Containers
        layoutHomeTab = findViewById(R.id.layoutHomeTab);
        layoutManualTab = findViewById(R.id.layoutManualTab);
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

        setupBottomNavigation();
        setupActionListeners();

        loadDashboardData();
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchTab(0);
                return true;
            } else if (id == R.id.nav_scanner) {
                Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
                scannerLauncher.launch(intent);
                return true;
            } else if (id == R.id.nav_manual) {
                switchTab(1);
                return true;
            }
            return false;
        });
    }

    private void switchTab(int tabIndex) {
        layoutHomeTab.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutManualTab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);

        if (tabIndex == 0) {
            loadDashboardData();
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
                etManualInputTag.setError("Please enter an asset tag or barcode");
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

        // Exit / Back to Welcome Portal Button
        findViewById(R.id.btnMainLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Exit Scanner")
                    .setMessage("Return to welcome screen?")
                    .setPositiveButton("Exit", (d, w) -> {
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Server connection info dialog
        tvLiveSyncStatus.setOnClickListener(v -> showServerConnectionDialog());
    }

    private void showServerConnectionDialog() {
        final EditText input = new EditText(this);
        input.setText(apiClient.getServerUrl());
        input.setSingleLine(true);
        input.setPadding(40, 30, 40, 30);
        input.setBackgroundResource(R.drawable.bg_edit_text);

        new AlertDialog.Builder(this)
                .setTitle("REST API Server Connection")
                .setMessage("Active Cloud Endpoint:\n" + apiClient.getServerUrl() + "\n\nYou can customize or reset to default Render cloud server:")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        apiClient.setServerUrl(newUrl);
                        loadDashboardData();
                        Toast.makeText(this, "Connected: " + apiClient.getServerUrl(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Reset Default", (d, w) -> {
                    apiClient.setServerUrl(ApiClient.PRODUCTION_URL);
                    loadDashboardData();
                    Toast.makeText(this, "Reset to Render Cloud: " + ApiClient.PRODUCTION_URL, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    public void loadDashboardData() {
        swipeRefreshScans.setRefreshing(true);

        apiClient.getScanLogs(20, new ApiClient.ApiCallback<List<ScanLog>>() {
            @Override
            public void onSuccess(List<ScanLog> logs) {
                swipeRefreshScans.setRefreshing(false);
                tvLiveSyncStatus.setText("● Connected: Render Cloud");
                if (logs != null && !logs.isEmpty()) {
                    scanLogAdapter.setScanLogs(logs);
                    rvRecentScans.setVisibility(View.VISIBLE);
                    layoutEmptyScans.setVisibility(View.GONE);
                } else {
                    scanLogAdapter.setScanLogs(null);
                    rvRecentScans.setVisibility(View.GONE);
                    layoutEmptyScans.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                swipeRefreshScans.setRefreshing(false);
                tvLiveSyncStatus.setText("⚠ Offline / Error");
            }
        });

        apiClient.getStats(new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject stats) {
                if (stats != null && stats.has("scanned_today")) {
                    int scannedToday = stats.get("scanned_today").getAsInt();
                    tvScannedTodayCount.setText(String.valueOf(scannedToday));
                }
            }

            @Override
            public void onError(String message) {}
        });
    }

    private void performManualTagAudit(String assetTag) {
        JsonObject payload = new JsonObject();
        payload.addProperty("asset_tag", assetTag);
        payload.addProperty("action_type", "Audit Verify");
        payload.addProperty("scanned_by", currentUser.getFullName());
        payload.addProperty("remarks", "Manual tag audit entry");

        apiClient.submitScanLog(payload, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Toast.makeText(MainActivity.this, "Tag [" + assetTag + "] successfully saved!", Toast.LENGTH_SHORT).show();
                etManualInputTag.setText("");
                bottomNavigation.setSelectedItemId(R.id.nav_home);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "Save error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
