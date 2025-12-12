package com.notified.preference.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private String telegramChatId;
    private String telegramUsername;

    private String preference;

    private List<String> preferences = new ArrayList<>();

    private Set<NotificationChannel> enabledChannels = new HashSet<>();

    // Notification frequency in minutes (default: 60 minutes = 1 hour)
    private Integer notificationIntervalMinutes = 60;
    
    // Track when the last notification was sent to this user
    private LocalDateTime lastNotificationSent;

    public enum NotificationChannel {
        EMAIL, WHATSAPP, APP, SMS, TELEGRAM
    }

    // Constructors
    public UserPreference() {
    }

    public UserPreference(String userId, String email, String phoneNumber, String preference) {
        this.userId = userId;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.preference = preference;
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

    public boolean isEmailEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.EMAIL);
    }

    public boolean isWhatsappEnabled() {
        return enabledChannels != null && enabledChannels.contains(NotificationChannel.WHATSAPP);
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }

    public List<String> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<String> preferences) {
        this.preferences = preferences;
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
}
