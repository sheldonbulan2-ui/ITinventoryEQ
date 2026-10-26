package com.example.it_inventoryeq.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * IT Equipment Item Model
 * Maps directly to Firestore 'equipment_items' collection and REST API.
 */
public class EquipmentItem implements Serializable {
    @SerializedName("item_id")
    private String itemId;

    @SerializedName("asset_tag")
    private String assetTag;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName("category")
    private String category; // Laptop, Desktop, Monitor, Network, Peripheral

    @SerializedName("serial_number")
    private String serialNumber;

    @SerializedName("status")
    private String status; // Available, Assigned, Maintenance, Defective

    @SerializedName("assigned_to")
    private String assignedTo;

    @SerializedName("location")
    private String location;

    @SerializedName("last_scanned_at")
    private String lastScannedAt;

    @SerializedName("last_scanned_by")
    private String lastScannedBy;

    public EquipmentItem() {
        // Default constructor required for Firestore
    }

    public EquipmentItem(String itemId, String assetTag, String itemName, String category,
                         String serialNumber, String status, String assignedTo,
                         String location, String lastScannedAt, String lastScannedBy) {
        this.itemId = itemId;
        this.assetTag = assetTag;
        this.itemName = itemName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.status = status;
        this.assignedTo = assignedTo;
        this.location = location;
        this.lastScannedAt = lastScannedAt;
        this.lastScannedBy = lastScannedBy;
    }

    // Getters and Setters
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getStatus() { return status != null ? status : "Available"; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedTo() { return assignedTo != null ? assignedTo : ""; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getLocation() { return location != null ? location : "IT Storage"; }
    public void setLocation(String location) { this.location = location; }

    public String getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(String lastScannedAt) { this.lastScannedAt = lastScannedAt; }

    public String getLastScannedBy() { return lastScannedBy; }
    public void setLastScannedBy(String lastScannedBy) { this.lastScannedBy = lastScannedBy; }
}
