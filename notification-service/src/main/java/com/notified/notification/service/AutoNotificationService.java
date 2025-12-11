package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    private final UserPreferenceClient preferenceClient;
    private final NotificationService notificationService;
    private final RssNewsService rssNewsService;
    private final RestTemplate restTemplate = new RestTemplate();

    // Category emojis for formatting
    private static final Map<String, String> CATEGORY_EMOJIS = new HashMap<>();

    static {
        CATEGORY_EMOJIS.put("SPORTS", "⚽");
        CATEGORY_EMOJIS.put("NEWS", "📰");
        CATEGORY_EMOJIS.put("WEATHER", "🌤️");
        CATEGORY_EMOJIS.put("SHOPPING", "🛒");
        CATEGORY_EMOJIS.put("FINANCE", "💰");
        CATEGORY_EMOJIS.put("ENTERTAINMENT", "🎬");
        CATEGORY_EMOJIS.put("HEALTH", "🏥");
        CATEGORY_EMOJIS.put("TECHNOLOGY", "💻");
        CATEGORY_EMOJIS.put("TRAVEL", "✈️");
        CATEGORY_EMOJIS.put("SOCIAL", "👥");
        CATEGORY_EMOJIS.put("EDUCATION", "📚");
        CATEGORY_EMOJIS.put("PROMOTIONS", "🎁");
    }

    public AutoNotificationService(UserPreferenceClient preferenceClient, 
                                   NotificationService notificationService,
                                   RssNewsService rssNewsService) {
        this.preferenceClient = preferenceClient;
        this.notificationService = notificationService;
        this.rssNewsService = rssNewsService;
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
            
            // Collect all unique categories from eligible users
            Set<String> allCategories = new HashSet<>();
            for (UserPreference pref : eligibleUsers) {
                if (pref.getPreferences() != null) {
                    allCategories.addAll(pref.getPreferences());
                }
            }

            if (allCategories.isEmpty()) {
                logger.info("No categories selected by any eligible user. Skipping notifications.");
                return;
            }

            // Fetch news for all categories using FREE RSS feeds
            logger.info("Fetching news from RSS feeds for categories: {}", allCategories);
            Map<String, List<RssNewsService.NewsArticle>> newsByCategory = 
                rssNewsService.fetchNewsForCategories(new ArrayList<>(allCategories));

            if (newsByCategory.isEmpty()) {
                logger.warn("No news articles fetched from RSS feeds. Skipping notifications.");
                return;
            }

            Random random = new Random();
            int successCount = 0;
            int failCount = 0;

            // Send personalized news to each eligible user
            for (UserPreference pref : eligibleUsers) {
                try {
                    List<String> userPreferences = pref.getPreferences();
                    
                    if (userPreferences == null || userPreferences.isEmpty()) {
                        logger.debug("User {} has no category preferences set, skipping", pref.getUserId());
                        continue;
                    }

                    // Pick a random category from user's preferences
                    String randomCategory = userPreferences.get(random.nextInt(userPreferences.size())).toUpperCase();
                    
                    // Get news articles for that category
                    List<RssNewsService.NewsArticle> articles = newsByCategory.get(randomCategory);
                    if (articles == null || articles.isEmpty()) {
                        logger.debug("No articles available for category: {} for user: {}", randomCategory, pref.getUserId());
                        continue;
                    }
                    
                    // Pick a random article
                    RssNewsService.NewsArticle article = articles.get(random.nextInt(articles.size()));
                    
                    // Create and send notification with real news
                    Notification notification = new Notification();
                    notification.setUserId(pref.getUserId());
                    
                    String emoji = CATEGORY_EMOJIS.getOrDefault(randomCategory, "📬");
                    notification.setSubject(emoji + " " + randomCategory + " News");
                    notification.setMessage(article.toNotificationMessage() + "\n\n📅 " + 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
                    
                    notificationService.sendNotification(notification);
                    
                    // Update the last notification timestamp
                    updateLastNotificationSent(pref);
                    
                    logger.info("✅ Sent {} news to user: {} (interval: {} mins) - '{}'", 
                        randomCategory, pref.getUserId(), pref.getNotificationIntervalMinutes(),
                        article.getTitle() != null ? article.getTitle().substring(0, Math.min(50, article.getTitle().length())) + "..." : "");
                    successCount++;
                    
                } catch (Exception e) {
                    logger.error("❌ Failed to send notification to user: {} - {}", 
                        pref.getUserId(), e.getMessage());
                    failCount++;
                }
            }

            logger.info("📊 Automatic notification batch complete: {} sent, {} failed", successCount, failCount);

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
