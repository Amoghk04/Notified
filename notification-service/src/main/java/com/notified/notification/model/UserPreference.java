package com.notified.notification.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UserPreference {

    private String id;
    private String userId;
    private String email;
    private String phoneNumber;
    private String telegramChatId;
    private String telegramUsername;
    private Set<NotificationChannel> enabledChannels;
    private String preference;
    private List<String> preferences = new ArrayList<>();
    
    // Notification frequency in minutes (default: 60 minutes = 1 hour)
    private Integer notificationIntervalMinutes = 60;
    
    // Track when the last notification was sent to this user
    private LocalDateTime lastNotificationSent;

    public enum NotificationChannel {
        EMAIL, WHATSAPP, APP, SMS, TELEGRAM
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

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public void setTelegramUsername(String telegramUsername) {
        this.telegramUsername = telegramUsername;
    }

    public Set<NotificationChannel> getEnabledChannels() {
        return enabledChannels;
    }

    public void setEnabledChannels(Set<NotificationChannel> enabledChannels) {
        this.enabledChannels = enabledChannels;
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }

    public boolean isEmailEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.EMAIL);
    }

    public boolean isWhatsappEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.WHATSAPP);
    }

    public boolean isAppEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.APP);
    }

    public boolean isTelegramEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.TELEGRAM);
    }

    public Integer getNotificationIntervalMinutes() {
        return notificationIntervalMinutes != null ? notificationIntervalMinutes : 60;
    }

    public void setNotificationIntervalMinutes(Integer notificationIntervalMinutes) {
        this.notificationIntervalMinutes = notificationIntervalMinutes;
    }

    public LocalDateTime getLastNotificationSent() {
        return lastNotificationSent;
    }

    public void setLastNotificationSent(LocalDateTime lastNotificationSent) {
        this.lastNotificationSent = lastNotificationSent;
    }

    public List<String> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<String> preferences) {
        this.preferences = preferences;
    }
}
