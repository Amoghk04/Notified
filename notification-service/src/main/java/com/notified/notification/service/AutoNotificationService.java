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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    private final UserPreferenceClient preferenceClient;
    private final NotificationService notificationService;
    private final NewsApiService newsApiService;

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
                                   NewsApiService newsApiService) {
        this.preferenceClient = preferenceClient;
        this.notificationService = notificationService;
        this.newsApiService = newsApiService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendInitialNotifications() {
        logger.info("🚀 Application ready - Sending automatic notifications to all registered users...");
        
        // Small delay to ensure all services are fully initialized
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sendNotificationsToAllUsers();
    }

    public void sendNotificationsToAllUsers() {
        try {
            List<UserPreference> allPreferences = preferenceClient.getAllPreferences();
            
            if (allPreferences == null || allPreferences.isEmpty()) {
                logger.info("No registered users found. Skipping automatic notifications.");
                return;
            }

            logger.info("Found {} registered users. Fetching news and sending personalized notifications...", allPreferences.size());
            
            // Collect all unique categories from all users
            Set<String> allCategories = new HashSet<>();
            for (UserPreference pref : allPreferences) {
                if (pref.getPreferences() != null) {
                    allCategories.addAll(pref.getPreferences());
                }
            }

            if (allCategories.isEmpty()) {
                logger.info("No categories selected by any user. Skipping notifications.");
                return;
            }

            // Fetch news for all categories at once (to minimize API calls)
            logger.info("Fetching news for categories: {}", allCategories);
            Map<String, List<NewsApiService.NewsArticle>> newsByCategory = 
                newsApiService.fetchNewsForCategories(new ArrayList<>(allCategories));

            if (newsByCategory.isEmpty()) {
                logger.warn("No news articles fetched. Skipping notifications.");
                return;
            }

            Random random = new Random();
            int successCount = 0;
            int failCount = 0;

            // Send personalized news to each user
            for (UserPreference pref : allPreferences) {
                try {
                    List<String> userPreferences = pref.getPreferences();
                    
                    if (userPreferences == null || userPreferences.isEmpty()) {
                        logger.debug("User {} has no category preferences set, skipping", pref.getUserId());
                        continue;
                    }

                    // Pick a random category from user's preferences
                    String randomCategory = userPreferences.get(random.nextInt(userPreferences.size())).toUpperCase();
                    
                    // Get news articles for that category
                    List<NewsApiService.NewsArticle> articles = newsByCategory.get(randomCategory);
                    if (articles == null || articles.isEmpty()) {
                        logger.debug("No articles available for category: {} for user: {}", randomCategory, pref.getUserId());
                        continue;
                    }
                    
                    // Pick a random article
                    NewsApiService.NewsArticle article = articles.get(random.nextInt(articles.size()));
                    
                    // Create and send notification with real news
                    Notification notification = new Notification();
                    notification.setUserId(pref.getUserId());
                    
                    String emoji = CATEGORY_EMOJIS.getOrDefault(randomCategory, "📬");
                    notification.setSubject(emoji + " " + randomCategory + " News");
                    notification.setMessage(article.toNotificationMessage() + "\n\n📅 " + 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
                    
                    notificationService.sendNotification(notification);
                    
                    logger.info("✅ Sent {} news to user: {} - '{}'", 
                        randomCategory, pref.getUserId(), 
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
     * Scheduled task that sends notifications every 60 seconds.
     * Adjust the fixedRate value to change the interval (in milliseconds).
     */
    @Scheduled(fixedRate = 60000)  // Every 60 seconds
    public void scheduledNotifications() {
        logger.info("⏰ Scheduled notification trigger - sending notifications to all users...");
        sendNotificationsToAllUsers();
    }
}
