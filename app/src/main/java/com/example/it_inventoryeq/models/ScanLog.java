package com.example.it_inventoryeq.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Scan Log (Audit Trail) Model
 * Maps directly to Firestore 'scan_logs' collection and REST API.
 */
public class ScanLog implements Serializable {
    @SerializedName("log_id")
    private String logId;

    @SerializedName("asset_tag")
    private String assetTag;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName("action_type")
    private String actionType; // Audit Verify, Check-Out / Assign, Check-In, Report Defective, New Registration

    @SerializedName("scanned_by")
    private String scannedBy;

    @SerializedName("location")
    private String location;

    @SerializedName("remarks")
    private String remarks;

    @SerializedName("timestamp")
    private String timestamp;

    public ScanLog() {
        // Default constructor required for Firestore
    }

    public ScanLog(String logId, String assetTag, String itemName, String actionType,
                   String scannedBy, String location, String remarks, String timestamp) {
        this.logId = logId;
        this.assetTag = assetTag;
        this.itemName = itemName;
        this.actionType = actionType;
        this.scannedBy = scannedBy;
        this.location = location;
        this.remarks = remarks;
        this.timestamp = timestamp;
    }

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getItemName() { return itemName != null ? itemName : "Equipment"; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getActionType() { return actionType != null ? actionType : "Audit Verify"; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getScannedBy() { return scannedBy != null ? scannedBy : "Staff"; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }

    public String getLocation() { return location != null ? location : ""; }
    public void setLocation(String location) { this.location = location; }

    public String getRemarks() { return remarks != null ? remarks : ""; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
