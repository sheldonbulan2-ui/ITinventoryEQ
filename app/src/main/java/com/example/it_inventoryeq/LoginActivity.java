package com.example.it_inventoryeq;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.google.android.material.button.MaterialButton;

/**
 * Minimal & Modern Welcome Screen.
 * Displays brand logo and a single "CLICK ME" button to launch the scanner console.
 */
public class LoginActivity extends AppCompatActivity {

    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClient.getInstance(this);

        setContentView(R.layout.activity_login);

        MaterialButton btnClickMe = findViewById(R.id.btnClickMe);

        btnClickMe.setOnClickListener(v -> {
            // Ensure default active auditor user is present
            User user = apiClient.getSavedUser();
            if (user == null) {
                user = new User("usr_staff", "IT Field Staff", "staff@itinventory.local", "scanner");
                apiClient.saveUser(user);
            }

            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
