package com.example.it_inventoryeq;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.google.android.material.button.MaterialButton;

/**
 * Modern Launch & Welcome Portal.
 * Instant access: Open Scanner Console without username/password restriction.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etStaffName;
    private MaterialButton btnOpenScanner;
    private TextView tvServerDisplay;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClient.getInstance(this);

        setContentView(R.layout.activity_login);

        etStaffName = findViewById(R.id.etStaffName);
        btnOpenScanner = findViewById(R.id.btnOpenScanner);
        tvServerDisplay = findViewById(R.id.tvServerDisplay);

        // Pre-fill existing user if available
        User savedUser = apiClient.getSavedUser();
        if (savedUser != null && savedUser.getFullName() != null && !savedUser.getFullName().isEmpty()) {
            etStaffName.setText(savedUser.getFullName());
        }

        updateServerDisplay();

        // Server Settings Quick Config
        findViewById(R.id.btnServerSettings).setOnClickListener(v -> showServerSettingsDialog());

        // Instant Launch into Main Dashboard
        btnOpenScanner.setOnClickListener(v -> launchApp());
    }

    private void updateServerDisplay() {
        if (tvServerDisplay != null) {
            tvServerDisplay.setText("Backend: " + apiClient.getServerUrl());
        }
    }

    private void launchApp() {
        String staffName = etStaffName.getText().toString().trim();
        if (staffName.isEmpty()) {
            staffName = "IT Field Staff";
        }

        User user = new User("usr_staff_" + (System.currentTimeMillis() % 10000), staffName, "staff@itinventory.local", "scanner");
        apiClient.saveUser(user);

        Toast.makeText(this, "Welcome, " + staffName, Toast.LENGTH_SHORT).show();
        navigateToMain();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showServerSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        final EditText etUrl = dialogView.findViewById(R.id.etServerUrlSetting);
        etUrl.setText(apiClient.getServerUrl());

        new AlertDialog.Builder(this)
                .setTitle("Backend Server URL")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newUrl = etUrl.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        apiClient.setServerUrl(newUrl);
                        updateServerDisplay();
                        Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Reset Default", (d, w) -> {
                    apiClient.setServerUrl(ApiClient.DEFAULT_EMULATOR_URL);
                    updateServerDisplay();
                })
                .show();
    }
}
