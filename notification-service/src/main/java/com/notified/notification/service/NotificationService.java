package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final UserPreferenceClient preferenceClient;
    private final NotificationChannelService channelService;

    public NotificationService(NotificationRepository repository, 
                              UserPreferenceClient preferenceClient,
                              NotificationChannelService channelService) {
        this.repository = repository;
        this.preferenceClient = preferenceClient;
        this.channelService = channelService;
    }

    public List<Notification> getAllNotifications() {
        return repository.findAll();
    }

    public List<Notification> getNotificationsByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    public Optional<Notification> getNotificationById(String id) {
        return repository.findById(id);
    }

    public Notification sendNotification(Notification notification) {
        try {
            // Fetch user preferences
            UserPreference preference = preferenceClient.getUserPreference(notification.getUserId());
            
            logger.info("Sending notification to user: {}", notification.getUserId());
            
            // Send via enabled channels based on preferences
            if (preference.isEmailEnabled()) {
                try {
                    channelService.sendEmailNotification(preference, notification);
                } catch (Exception ex) {
                    logger.warn("Email channel failed for userId={} notificationId={} error={}", notification.getUserId(), notification.getId(), ex.getMessage());
                }
            }
            
            if (preference.isWhatsappEnabled()) {
                try {
                    channelService.sendWhatsappNotification(preference, notification);
                } catch (Exception ex) {
                    logger.warn("WhatsApp channel failed for userId={} notificationId={} error={}", notification.getUserId(), notification.getId(), ex.getMessage());
                }
            }
            
            if (preference.isTelegramEnabled()) {
                try {
                    channelService.sendTelegramNotification(preference, notification);
                } catch (Exception ex) {
                    logger.warn("Telegram channel failed for userId={} notificationId={} error={}", notification.getUserId(), notification.getId(), ex.getMessage());
                }
            }

            if (preference.isAppEnabled()) {
                channelService.sendAppNotification(preference, notification);
            }
            
            // Populate channels from preference (reflect attempted channels) with enum translation
            if (preference.getEnabledChannels() != null) {
                java.util.Set<com.notified.notification.model.Notification.NotificationChannel> channels =
                        preference.getEnabledChannels().stream()
                                .map(c -> com.notified.notification.model.Notification.NotificationChannel.valueOf(c.name()))
                                .collect(java.util.stream.Collectors.toSet());
                notification.setChannels(channels);
            }

            // Update notification status
            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Failed to send notification to user: {}", notification.getUserId(), e);
            notification.setStatus(Notification.NotificationStatus.FAILED);
        }
        
        return repository.save(notification);
    }

    public void deleteNotification(String id) {
        repository.deleteById(id);
    }
}
