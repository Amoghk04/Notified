package com.notified.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.Set;

@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Message is required")
    private String message;

    private String subject;

    private Set<NotificationChannel> channels;

    private NotificationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public enum NotificationChannel {
        EMAIL, SMS, APP
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }

    // Constructors
    public Notification() {
        this.createdAt = LocalDateTime.now();
        this.status = NotificationStatus.PENDING;
    }

    public Notification(String userId, String message, String subject) {
        this();
        this.userId = userId;
        this.message = message;
        this.subject = subject;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Set<NotificationChannel> getChannels() {
        return channels;
    }

    public void setChannels(Set<NotificationChannel> channels) {
        this.channels = channels;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
