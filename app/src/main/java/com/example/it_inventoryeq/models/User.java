package com.example.it_inventoryeq.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * User Model
 */
public class User implements Serializable {
    @SerializedName("user_id")
    private String userId;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("email")
    private String email;

    @SerializedName("role")
    private String role; // admin, scanner

    public User() {}

    public User(String userId, String fullName, String email, String role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName != null ? fullName : "IT Staff"; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role != null ? role : "scanner"; }
    public void setRole(String role) { this.role = role; }
}
