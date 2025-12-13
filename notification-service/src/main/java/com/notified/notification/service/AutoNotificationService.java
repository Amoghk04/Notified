package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.NewsArticle;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    private final UserPreferenceClient preferenceClient;
    private final NotificationService notificationService;
    private final NewsArticleService newsArticleService;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelService channelService;
    private final RestTemplate restTemplate = new RestTemplate();

    public AutoNotificationService(UserPreferenceClient preferenceClient, 
                                   NotificationService notificationService,
                                   NewsArticleService newsArticleService,
                                   NotificationRepository notificationRepository,
                                   NotificationChannelService channelService) {
        this.preferenceClient = preferenceClient;
        this.notificationService = notificationService;
        this.newsArticleService = newsArticleService;
        this.notificationRepository = notificationRepository;
        this.channelService = channelService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendInitialNotifications() {
        logger.info("🚀 Application ready - Will check and send notifications based on user preferences...");
        
        // Small delay to ensure all services are fully initialized
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sendNotificationsToEligibleUsers();
    }

    /**
     * Check if a user is due for a notification based on their interval settings
     */
    private boolean isUserDueForNotification(UserPreference pref) {
        LocalDateTime lastSent = pref.getLastNotificationSent();
        int intervalMinutes = pref.getNotificationIntervalMinutes();
        
        // If never sent, user is due
        if (lastSent == null) {
            return true;
        }
        
        // Check if enough time has passed
        long minutesSinceLastNotification = ChronoUnit.MINUTES.between(lastSent, LocalDateTime.now());
        return minutesSinceLastNotification >= intervalMinutes;
    }

    /**
     * Update the lastNotificationSent timestamp for a user
     */
    private void updateLastNotificationSent(UserPreference pref) {
        try {
            pref.setLastNotificationSent(LocalDateTime.now());
            
            String apiUrl = "http://localhost:8081/preferences/" + pref.getUserId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserPreference> entity = new HttpEntity<>(pref, headers);
            
            restTemplate.exchange(apiUrl, HttpMethod.PUT, entity, UserPreference.class);
            logger.debug("Updated lastNotificationSent for user: {}", pref.getUserId());
        } catch (Exception e) {
            logger.warn("Failed to update lastNotificationSent for user: {} - {}", pref.getUserId(), e.getMessage());
        }
    }

    public void sendNotificationsToEligibleUsers() {
        try {
            List<UserPreference> allPreferences = preferenceClient.getAllPreferences();
            
            if (allPreferences == null || allPreferences.isEmpty()) {
                logger.info("No registered users found. Skipping automatic notifications.");
                return;
            }

            // Filter users who are due for notifications
            List<UserPreference> eligibleUsers = new ArrayList<>();
            for (UserPreference pref : allPreferences) {
                if (isUserDueForNotification(pref)) {
                    eligibleUsers.add(pref);
                }
            }

            if (eligibleUsers.isEmpty()) {
                logger.debug("No users are due for notifications at this time.");
                return;
            }

            logger.info("Found {} users due for notifications out of {} total users", 
                eligibleUsers.size(), allPreferences.size());

            int successCount = 0;
            int failCount = 0;

            // Send personalized news to each eligible user
            for (UserPreference pref : eligibleUsers) {
                try {
                    List<String> userCategories = pref.getPreferences();
                    
                    if (userCategories == null || userCategories.isEmpty()) {
                        logger.debug("User {} has no category preferences set, skipping", pref.getUserId());
                        continue;
                    }
                    // Collect top 3 newest articles across all user's categories
                    List<NewsArticle> allArticles = new ArrayList<>();
                    for (String category : userCategories) {
                        try {
                            // Get latest 3 articles from this category ordered by published date
                            List<NewsArticle> articles = newsArticleService.getArticlesByCategory(category, 3);
                            
                            for (NewsArticle article : articles) {
                                // Check if this user has already received this article
                                if (!notificationRepository.existsByUserIdAndArticleContentHash(
                                        pref.getUserId(), article.getContentHash())) {
                                    allArticles.add(article);
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error fetching {} news for user {}: {}", category, pref.getUserId(), e.getMessage());
                        }
                    }

                    if (allArticles.isEmpty()) {
                        logger.debug("No new articles available for user: {}", pref.getUserId());
                        continue;
                    }

                    // Sort all articles by publishedDate descending (newest first)
                    allArticles.sort((a, b) -> {
                        LocalDateTime dateA = a.getPublishedDate();
                        LocalDateTime dateB = b.getPublishedDate();
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA);
                    });

                    // Get top 3 most recent articles
                    int articlesToSend = Math.min(3, allArticles.size());
                    List<NewsArticle> top3Articles = allArticles.subList(0, articlesToSend);

                    // Generate a batch ID for this notification batch
                    String batchId = java.util.UUID.randomUUID().toString().substring(0, 8);

                    // Send consolidated EMAIL first (single email with all articles)
                    if (pref.isEmailEnabled()) {
                        try {
                            channelService.sendConsolidatedEmailNotification(pref, top3Articles, batchId);
                            logger.info("Sent consolidated email with {} articles to user {}", 
                                articlesToSend, pref.getUserId());
                        } catch (Exception ex) {
                            logger.warn("Consolidated email failed for userId={} batchId={} error={}", 
                                pref.getUserId(), batchId, ex.getMessage());
                        }
                    }

                    // Send each article as a separate TELEGRAM message (for reaction buttons)
                    for (NewsArticle article : top3Articles) {
                        Notification notification = new Notification();
                        notification.setUserId(pref.getUserId());
                        notification.setSubject("📰 " + article.getCategory() + " News: " + article.getTitle());
                        notification.setArticleContentHash(article.getContentHash());

                        StringBuilder message = new StringBuilder();
                        message.append("📌 ").append(article.getCategory().toUpperCase()).append("\n\n");
                        message.append("📰 ").append(article.getTitle()).append("\n\n");
                        if (article.getDescription() != null && !article.getDescription().isEmpty()) {
                            message.append(article.getDescription()).append("\n\n");
                        }
                        message.append("🔗 ").append(article.getLink()).append("\n");
                        message.append("📅 Source: ").append(article.getSource());

                        notification.setMessage(message.toString());
                        notification.setStatus(Notification.NotificationStatus.PENDING);

                        // IMPORTANT: Save notification FIRST to get an ID for the reaction buttons
                        notification = notificationRepository.save(notification);

                        // Send via Telegram (each article separate for reaction buttons)
                        try {
                            if (pref.isTelegramEnabled()) {
                                try {
                                    channelService.sendTelegramNotification(pref, notification);
                                } catch (Exception ex) {
                                    logger.warn("Telegram channel failed for userId={} notificationId={} error={}", 
                                        pref.getUserId(), notification.getId(), ex.getMessage());
                                }
                            }
                            
                            if (pref.isWhatsappEnabled()) {
                                try {
                                    channelService.sendWhatsappNotification(pref, notification);
                                } catch (Exception ex) {
                                    logger.warn("WhatsApp channel failed for userId={} notificationId={} error={}", 
                                        pref.getUserId(), notification.getId(), ex.getMessage());
                                }
                            }
                            
                            if (pref.isAppEnabled()) {
                                channelService.sendAppNotification(pref, notification);
                            }
                            
                            // Set channels from preference
                            if (pref.getEnabledChannels() != null) {
                                Set<Notification.NotificationChannel> channels =
                                        pref.getEnabledChannels().stream()
                                                .map(c -> Notification.NotificationChannel.valueOf(c.name()))
                                                .collect(java.util.stream.Collectors.toSet());
                                notification.setChannels(channels);
                            }
                            
                            notification.setStatus(Notification.NotificationStatus.SENT);
                            notification.setSentAt(LocalDateTime.now());
                            // Save again to update status and telegramMessageId
                            notificationRepository.save(notification);
                            logger.debug("Sent article '{}' to user {}", article.getTitle(), pref.getUserId());
                        } catch (Exception e) {
                            logger.error("Failed to send article '{}' to user {}: {}", 
                                article.getTitle(), pref.getUserId(), e.getMessage());
                            notification.setStatus(Notification.NotificationStatus.FAILED);
                            notificationRepository.save(notification);
                        }
                    }

                    // Update the last notification timestamp
                    updateLastNotificationSent(pref);

                    logger.info("✅ Sent {} news articles to user: {} (interval: {} mins)", 
                        articlesToSend, pref.getUserId(), pref.getNotificationIntervalMinutes());
                    successCount++;
                    
                } catch (Exception e) {
                    logger.error("❌ Failed to send notification to user: {} - {}", 
                        pref.getUserId(), e.getMessage());
                    failCount++;
                }
            }

            logger.info("📊 Automatic notification batch complete: {} users processed, {} failed", successCount, failCount);

        } catch (Exception e) {
            logger.error("Failed to fetch user preferences for automatic notifications: {}", e.getMessage());
        }
    }

    /**
     * Scheduled task that checks every minute for users who are due for notifications.
     * Each user will receive notifications based on their individual interval settings.
     */
    @Scheduled(fixedRate = 60000)  // Check every 60 seconds
    public void scheduledNotifications() {
        logger.debug("⏰ Scheduled check - looking for users due for notifications...");
        sendNotificationsToEligibleUsers();
    }
}
