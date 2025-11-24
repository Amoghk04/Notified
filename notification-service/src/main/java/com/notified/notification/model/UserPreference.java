package com.notified.notification.model;

import java.util.Set;

public class UserPreference {

    private String id;
    private String userId;
    private String email;
    private String phoneNumber;
    private Set<NotificationChannel> enabledChannels;

    public enum NotificationChannel {
        EMAIL, SMS, APP
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
