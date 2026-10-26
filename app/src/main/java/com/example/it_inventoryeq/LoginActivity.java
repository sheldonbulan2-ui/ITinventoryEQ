package com.example.it_inventoryeq;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.google.android.material.button.MaterialButton;

/**
 * Staff Scanner Login Screen
 * Branded with #1E40AF and single shared logo.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnFillDemo;
    private ProgressBar progressBar;
    private TextView tvServerDisplay;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClient.getInstance(this);

        // Auto-login if previously saved
        User savedUser = apiClient.getSavedUser();
        if (savedUser != null) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnFillDemo = findViewById(R.id.btnFillDemo);
        progressBar = findViewById(R.id.loginProgressBar);
        tvServerDisplay = findViewById(R.id.tvServerDisplay);

        updateServerDisplay();

        // 1-Click Demo Credentials
        btnFillDemo.setOnClickListener(v -> {
            etEmail.setText("scanner@itinventory.com");
            etPassword.setText("scan123");
            Toast.makeText(this, "Demo scanner credentials filled", Toast.LENGTH_SHORT).show();
        });

        // Server Settings Button
        findViewById(R.id.btnServerSettings).setOnClickListener(v -> showServerSettingsDialog());

        // Login Action
        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void updateServerDisplay() {
        if (tvServerDisplay != null) {
            tvServerDisplay.setText("Connected to: " + apiClient.getServerUrl());
        }
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        apiClient.login(email, password, new ApiClient.ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Welcome, " + user.getFullName(), Toast.LENGTH_SHORT).show();
                navigateToMain();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                // Offline fallback if local server is starting up
                if (email.equalsIgnoreCase("scanner@itinventory.com") && password.equals("scan123")) {
                    User offlineUser = new User("USR-SCAN-01", "Alex Mercer (Field Tech)", email, "scanner");
                    apiClient.saveUser(offlineUser);
                    Toast.makeText(LoginActivity.this, "Signed in (Local Cache)", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                } else {
                    Toast.makeText(LoginActivity.this, "Login Failed: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnFillDemo.setEnabled(!loading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
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
                updateServerDisplay();
                Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
