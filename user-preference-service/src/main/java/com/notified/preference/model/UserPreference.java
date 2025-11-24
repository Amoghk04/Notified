package com.notified.preference.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

@Document(collection = "user_preferences")
public class UserPreference {

    @Id
    private String id;

    @NotBlank(message = "User ID is required")
    private String userId;

    @Email(message = "Email should be valid")
    private String email;

    private String phoneNumber;

    private Set<NotificationChannel> enabledChannels = new HashSet<>();

    public enum NotificationChannel {
        EMAIL, SMS, APP
    }

    // Constructors
    public UserPreference() {
    }

    public UserPreference(String userId, String email, String phoneNumber) {
        this.userId = userId;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Set<NotificationChannel> getEnabledChannels() {
        return enabledChannels;
    }

    public void setEnabledChannels(Set<NotificationChannel> enabledChannels) {
        this.enabledChannels = enabledChannels;
    }

    public boolean isEmailEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.EMAIL);
    }

    public boolean isSmsEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.SMS);
    }

    public boolean isAppEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.APP);
    }
}
