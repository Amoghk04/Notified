package com.notified.notification.model;

import java.util.Set;

public class UserPreference {

    private String id;
    private String userId;
    private String email;
    private String phoneNumber;
    private String telegramChatId;
    private Set<NotificationChannel> enabledChannels;
    private String preference;

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
}
