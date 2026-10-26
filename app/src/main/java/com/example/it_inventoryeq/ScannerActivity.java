package com.example.it_inventoryeq;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.it_inventoryeq.database.LocalDatabaseHelper;
import com.example.it_inventoryeq.models.EquipmentItem;
import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
import com.example.it_inventoryeq.network.OfflineSyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Camera Scanner Activity with ZXing Barcode & QR Decoding.
 * Features: Manual Tag Input fallback + Offline-first database query & queuing.
 */
public class ScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 2001;

    private DecoratedBarcodeView barcodeView;
    private BeepManager beepManager;
    private ProgressBar progressBar;
    private ImageView btnToggleFlash;
    private MaterialButton btnScannerManualInput;
    private boolean isFlashOn = false;
    private boolean isProcessingScan = false;

    private ApiClient apiClient;
    private LocalDatabaseHelper localDb;
    private OfflineSyncManager syncManager;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        apiClient = ApiClient.getInstance(this);
        localDb = LocalDatabaseHelper.getInstance(this);
        syncManager = OfflineSyncManager.getInstance();
        currentUser = apiClient.getSavedUser();

        barcodeView = findViewById(R.id.barcodeScannerView);
        progressBar = findViewById(R.id.scannerProgressBar);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        btnScannerManualInput = findViewById(R.id.btnScannerManualInput);

        beepManager = new BeepManager(this);

        // Configure Barcode Formats (QR Code, Barcode 128, 39, EAN, Data Matrix)
        Collection<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.DATA_MATRIX
        );
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));

        // Back button
        findViewById(R.id.btnScannerBack).setOnClickListener(v -> finish());

        // Flashlight toggle
        btnToggleFlash.setOnClickListener(v -> toggleFlashlight());

        // Manual Input button (incase camera fails or barcode is damaged)
        btnScannerManualInput.setOnClickListener(v -> showManualInputDialog());

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startScanning();
        }
    }

    private void toggleFlashlight() {
        if (isFlashOn) {
            barcodeView.setTorchOff();
            isFlashOn = false;
        } else {
            barcodeView.setTorchOn();
            isFlashOn = true;
        }
    }

    private void startScanning() {
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || result.getText() == null || isProcessingScan) {
                    return;
                }

                String scannedCode = result.getText().trim();
                if (scannedCode.isEmpty()) return;

                isProcessingScan = true;
                barcodeView.pause();
                beepManager.playBeepSoundAndVibrate();

                // Show confirmation prompt with "Completed" and "Try Again"
                showScanConfirmationDialog(scannedCode);
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {}
        });
    }

    /**
     * Confirmation prompt shown when a barcode/QR code is detected.
     * Allows user to select "Completed" to view item details or "Try Again" to re-scan.
     */
    private void showScanConfirmationDialog(final String scannedCode) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_scan_confirmation, null);

        TextView tvScannedCode = dialogView.findViewById(R.id.tvScannedCodeDisplay);
        MaterialButton btnTryAgain = dialogView.findViewById(R.id.btnScanTryAgain);
        MaterialButton btnCompleted = dialogView.findViewById(R.id.btnScanCompleted);

        tvScannedCode.setText(scannedCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnTryAgain.setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });

        btnCompleted.setOnClickListener(v -> {
            dialog.dismiss();
            handleScannedAssetTag(scannedCode);
        });

        dialog.show();
    }

    /**
     * Fallback dialog to type asset tag manually
     */
    private void showManualInputDialog() {
        barcodeView.pause();
        isProcessingScan = true;

        final EditText input = new EditText(this);
        input.setHint("e.g. IT-LAP-001");
        input.setSingleLine(true);
        input.setPadding(40, 30, 40, 30);
        input.setBackgroundResource(R.drawable.bg_edit_text);

        new AlertDialog.Builder(this)
                .setTitle("Manual Asset Tag Entry")
                .setMessage("Enter the equipment barcode / asset tag printed on the label:")
                .setView(input)
                .setPositiveButton("Search & Audit", (d, w) -> {
                    String tag = input.getText().toString().trim();
                    if (!tag.isEmpty()) {
                        handleScannedAssetTag(tag);
                    } else {
                        resumeScanning();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> resumeScanning())
                .setCancelable(false)
                .show();
    }

    /**
     * Query database for asset tag (Online or Offline SQLite Cache)
     */
    public void handleScannedAssetTag(String assetTag) {
        progressBar.setVisibility(View.VISIBLE);
        barcodeView.pause();

        // Check if offline
        if (!syncManager.isOnline(this)) {
            progressBar.setVisibility(View.GONE);
            EquipmentItem cachedItem = localDb.findEquipmentByTag(assetTag);
            if (cachedItem != null) {
                showExistingItemActionDialog(cachedItem, true);
            } else {
                showRegisterNewItemDialog(assetTag, true);
            }
            return;
        }

        // Online lookup with fallback to local SQLite cache
        apiClient.getEquipmentByTag(assetTag, new ApiClient.ApiCallback<EquipmentItem>() {
            @Override
            public void onSuccess(EquipmentItem item) {
                progressBar.setVisibility(View.GONE);
                if (item != null) {
                    localDb.upsertCachedEquipment(item);
                    showExistingItemActionDialog(item, false);
                } else {
                    showRegisterNewItemDialog(assetTag, false);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                // Network failed mid-request -> fallback to local SQLite cache
                EquipmentItem cachedItem = localDb.findEquipmentByTag(assetTag);
                if (cachedItem != null) {
                    showExistingItemActionDialog(cachedItem, true);
                } else {
                    showRegisterNewItemDialog(assetTag, true);
                }
            }
        });
    }

    /**
     * Post-Scan Dialog: Existing Equipment Item Found
     */
    private void showExistingItemActionDialog(EquipmentItem item, boolean isOffline) {
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

        // Populate fields
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

        // Configure Action Spinner
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

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });

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

            if (updatedLocation.isEmpty()) {
                updatedLocation = item.getLocation();
            }

            // Build atomic payload
            JsonObject payload = new JsonObject();
            payload.addProperty("asset_tag", item.getAssetTag());
            payload.addProperty("item_name", item.getItemName());
            payload.addProperty("category", item.getCategory());
            payload.addProperty("serial_number", item.getSerialNumber());
            payload.addProperty("action_type", actionType);
            payload.addProperty("new_status", newStatus);
            payload.addProperty("scanned_by", currentUser != null ? currentUser.getFullName() : "Mobile Scanner");
            payload.addProperty("location", updatedLocation);
            payload.addProperty("assigned_to", updatedAssigned);
            payload.addProperty("remarks", remarks);

            btnSaveSync.setEnabled(false);
            btnSaveSync.setText("Saving...");

            // Offline-first save: writes to server if online, or queues in local SQLite if offline!
            syncManager.submitScanOrQueue(ScannerActivity.this, payload, new OfflineSyncManager.ScanActionCallback() {
                @Override
                public void onSuccess(boolean isOfflineQueued, String message) {
                    dialog.dismiss();
                    Toast.makeText(ScannerActivity.this, message, Toast.LENGTH_SHORT).show();

                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("scanned_asset_tag", item.getAssetTag());
                    returnIntent.putExtra("is_offline", isOfflineQueued);
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                }

                @Override
                public void onError(String error) {
                    btnSaveSync.setEnabled(true);
                    btnSaveSync.setText("Save & Sync to Web");
                    Toast.makeText(ScannerActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    /**
     * Post-Scan Dialog: Item NOT in database -> Prompt to Register (Works Online & Offline!)
     */
    private void showRegisterNewItemDialog(String scannedAssetTag, boolean isOffline) {
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

        tvAssetTag.setText(scannedAssetTag);

        if (isOffline) {
            btnRegisterSync.setText("Queue Registration (Offline)");
        }

        // Categories
        String[] categories = new String[]{"Laptop", "Desktop", "Monitor", "Network", "Peripheral"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        spinnerCategory.setAdapter(catAdapter);

        // Statuses
        String[] statuses = new String[]{"Available", "Assigned", "Maintenance", "Defective"};
        ArrayAdapter<String> statAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses);
        spinnerStatus.setAdapter(statAdapter);

        etLocation.setText("IT Storage Room");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });

        btnRegisterSync.setOnClickListener(v -> {
            String name = etItemName.getText().toString().trim();
            if (name.isEmpty()) {
                etItemName.setError("Item name is required");
                return;
            }

            String category = (String) spinnerCategory.getSelectedItem();
            String status = (String) spinnerStatus.getSelectedItem();
            String serial = etSerial.getText().toString().trim();
            if (serial.isEmpty()) serial = "N/A";
            String location = etLocation.getText().toString().trim();
            if (location.isEmpty()) location = "IT Storage";
            String assignedTo = etAssignedTo.getText().toString().trim();

            JsonObject payload = new JsonObject();
            payload.addProperty("asset_tag", scannedAssetTag);
            payload.addProperty("item_name", name);
            payload.addProperty("category", category);
            payload.addProperty("serial_number", serial);
            payload.addProperty("new_status", status);
            payload.addProperty("location", location);
            payload.addProperty("assigned_to", assignedTo);
            payload.addProperty("action_type", "New Registration");
            payload.addProperty("is_registration", true);
            payload.addProperty("remarks", "Direct registration via Android scanner");
            payload.addProperty("scanned_by", currentUser != null ? currentUser.getFullName() : "Mobile Scanner");

            btnRegisterSync.setEnabled(false);
            btnRegisterSync.setText("Registering...");

            syncManager.submitScanOrQueue(ScannerActivity.this, payload, new OfflineSyncManager.ScanActionCallback() {
                @Override
                public void onSuccess(boolean isOfflineQueued, String message) {
                    dialog.dismiss();
                    Toast.makeText(ScannerActivity.this, message, Toast.LENGTH_SHORT).show();

                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("scanned_asset_tag", scannedAssetTag);
                    returnIntent.putExtra("is_offline", isOfflineQueued);
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                }

                @Override
                public void onError(String error) {
                    btnRegisterSync.setEnabled(true);
                    btnRegisterSync.setText("Register & Sync");
                    Toast.makeText(ScannerActivity.this, "Registration Error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void resumeScanning() {
        isProcessingScan = false;
        barcodeView.resume();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView.resume();
            isProcessingScan = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR and Barcodes", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
