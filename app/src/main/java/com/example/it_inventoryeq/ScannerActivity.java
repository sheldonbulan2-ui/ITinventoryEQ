package com.example.it_inventoryeq;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.it_inventoryeq.models.User;
import com.example.it_inventoryeq.network.ApiClient;
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
 * High-Speed Continuous Barcode & QR Camera Scanner.
 * Workflow: Detection -> [Try Again / Completed] -> Completed Auto-Saves to Database -> [OK] -> Resumes Next Scan!
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
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        apiClient = ApiClient.getInstance(this);
        currentUser = apiClient.getSavedUser();

        barcodeView = findViewById(R.id.barcodeScannerView);
        progressBar = findViewById(R.id.scannerProgressBar);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        btnScannerManualInput = findViewById(R.id.btnScannerManualInput);

        beepManager = new BeepManager(this);

        // Configure Barcode Formats (QR Code, Code 128, Code 39, EAN 13/8, Data Matrix)
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

        // Manual Input button fallback
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
     * User can select "Completed" (auto-saves to DB) or "Try Again" (resumes camera).
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

        // Action 1: Try Again -> resume scanning immediately
        btnTryAgain.setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });

        // Action 2: Completed -> auto-save to cloud database then show "OK" for next scan
        btnCompleted.setOnClickListener(v -> {
            dialog.dismiss();
            autoSaveAndConfirmNext(scannedCode);
        });

        dialog.show();
    }

    /**
     * Auto-saves the scanned code directly to the cloud database with high speed (< 2s),
     * then prompts the user with "OK" to immediately scan the next equipment.
     */
    private void autoSaveAndConfirmNext(final String scannedCode) {
        progressBar.setVisibility(View.VISIBLE);

        String staffName = currentUser != null ? currentUser.getFullName() : "IT Field Staff";

        JsonObject payload = new JsonObject();
        payload.addProperty("asset_tag", scannedCode);
        payload.addProperty("action_type", "Audit Verify");
        payload.addProperty("scanned_by", staffName);
        payload.addProperty("location", "Computer Laboratory 1");
        payload.addProperty("remarks", "Auto-verified via mobile barcode scanner");

        apiClient.submitScanLog(payload, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                progressBar.setVisibility(View.GONE);
                String itemName = "IT Equipment";
                String status = "Verified";
                String location = "Computer Laboratory 1";

                if (result != null) {
                    if (result.has("item") && !result.get("item").isJsonNull()) {
                        JsonObject itemObj = result.getAsJsonObject("item");
                        if (itemObj.has("item_name") && !itemObj.get("item_name").isJsonNull()) {
                            itemName = itemObj.get("item_name").getAsString();
                        }
                        if (itemObj.has("status") && !itemObj.get("status").isJsonNull()) {
                            status = itemObj.get("status").getAsString();
                        }
                        if (itemObj.has("location") && !itemObj.get("location").isJsonNull()) {
                            location = itemObj.get("location").getAsString();
                        }
                    } else if (result.has("log") && !result.get("log").isJsonNull()) {
                        JsonObject logObj = result.getAsJsonObject("log");
                        if (logObj.has("item_name") && !logObj.get("item_name").isJsonNull()) {
                            itemName = logObj.get("item_name").getAsString();
                        }
                    }
                }

                showScanSuccessNextDialog(scannedCode, itemName, status, location);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                showScanErrorDialog(scannedCode, error);
            }
        });
    }

    /**
     * Shows fast confirmation: "Saved to Database! Click OK to scan next item"
     */
    private void showScanSuccessNextDialog(String scannedCode, String itemName, String status, String location) {
        String msg = "Item: " + itemName + "\n"
                   + "Asset Tag: " + scannedCode + "\n"
                   + "Laboratory: " + location + "\n"
                   + "Status: " + status + "\n\n"
                   + "✓ Synchronized to Web Admin in real-time.\n"
                   + "Click OK to scan next item.";

        new AlertDialog.Builder(this)
                .setTitle("Auto-Saved to Database!")
                .setMessage(msg)
                .setPositiveButton("OK", (d, w) -> {
                    d.dismiss();
                    resumeScanning();
                })
                .setCancelable(false)
                .show();
    }

    private void showScanErrorDialog(final String scannedCode, String errorMsg) {
        new AlertDialog.Builder(this)
                .setTitle("Sync Connection Error")
                .setMessage("Asset Tag: " + scannedCode + "\n\n"
                        + "Failed to reach REST API:\n" + errorMsg + "\n\n"
                        + "Server URL: " + apiClient.getServerUrl() + "\n\n"
                        + "Please verify your internet connection or server status.")
                .setPositiveButton("Retry", (d, w) -> {
                    d.dismiss();
                    autoSaveAndConfirmNext(scannedCode);
                })
                .setNegativeButton("Scan Next", (d, w) -> {
                    d.dismiss();
                    resumeScanning();
                })
                .setCancelable(false)
                .show();
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
                .setTitle("Manual Tag Entry")
                .setMessage("Enter the barcode / asset tag to audit:")
                .setView(input)
                .setPositiveButton("Save & Sync", (d, w) -> {
                    String tag = input.getText().toString().trim();
                    if (!tag.isEmpty()) {
                        autoSaveAndConfirmNext(tag);
                    } else {
                        resumeScanning();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> resumeScanning())
                .setCancelable(false)
                .show();
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
