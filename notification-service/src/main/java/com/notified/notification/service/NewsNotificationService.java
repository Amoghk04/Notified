package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.NewsArticle;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NewsNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NewsNotificationService.class);

    private final UserPreferenceClient preferenceClient;
    private final NewsScraperService newsScraperService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelService channelService;

    public NewsNotificationService(UserPreferenceClient preferenceClient,
                                   NewsScraperService newsScraperService,
                                   NotificationService notificationService,
                                   NotificationRepository notificationRepository,
                                   NotificationChannelService channelService) {
        this.preferenceClient = preferenceClient;
        this.newsScraperService = newsScraperService;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.channelService = channelService;
    }

    @Scheduled(fixedRate = 3600000) // Run every 1 hour
    public void sendNewsNotificationsToUsers() {
        logger.info("Starting news notification distribution to users...");
        
        try {
            // Get all users with preferences
            List<UserPreference> allPreferences = preferenceClient.getAllPreferences();
            
            for (UserPreference userPref : allPreferences) {
                try {
                    sendNewsToUser(userPref);
                } catch (Exception e) {
                    logger.error("Error sending news to user: {}", userPref.getUserId(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in news notification distribution", e);
        }
        
        logger.info("Completed news notification distribution");
    }

    private void sendNewsToUser(UserPreference userPref) {
        // Get user's preferred categories from preferences array
        List<String> userCategories = userPref.getPreferences();
        
        if (userCategories == null || userCategories.isEmpty()) {
            logger.debug("User {} has no category preferences", userPref.getUserId());
            return;
        }

        logger.info("Sending news to user: {} for categories: {}", userPref.getUserId(), userCategories);

        // Collect all new articles across all categories, grouped by category
        Map<String, List<NewsArticle>> articlesByCategory = new HashMap<>();
        List<String> articleContentHashes = new ArrayList<>();
        int totalNewArticles = 0;
        
        for (String category : userCategories) {
            try {
                // Get latest 2 articles from this category ordered by published date
                List<NewsArticle> articles = newsScraperService.getArticlesByCategory(category, 2);
                List<NewsArticle> newArticles = new ArrayList<>();
                
                for (NewsArticle article : articles) {
                    // Check database if this user has already received this article
                    if (notificationRepository.existsByUserIdAndArticleContentHash(
                            userPref.getUserId(), article.getContentHash())) {
                        logger.debug("Skipping article '{}' - already sent to user {}", 
                            article.getTitle(), userPref.getUserId());
                        continue;
                    }
                    
                    newArticles.add(article);
                    articleContentHashes.add(article.getContentHash());
                    totalNewArticles++;
                }
                
                if (!newArticles.isEmpty()) {
                    articlesByCategory.put(category, newArticles);
                }
                
            } catch (Exception e) {
                logger.error("Error fetching {} news for user {}", category, userPref.getUserId(), e);
            }
        }
        
        // If there are new articles, send them in a single consolidated email
        if (totalNewArticles > 0) {
            try {
                // Send consolidated email (all articles in one message)
                if (userPref.isEmailEnabled()) {
                    sendConsolidatedEmail(userPref, articlesByCategory, userCategories, articleContentHashes, totalNewArticles);
                }
                
                // Send individual Telegram messages (one per article)
                if (userPref.isTelegramEnabled()) {
                    sendIndividualTelegramMessages(userPref, articlesByCategory, userCategories);
                }
                
                logger.info("Sent news notifications with {} articles to user {}", 
                    totalNewArticles, userPref.getUserId());
                
            } catch (Exception e) {
                logger.error("Error sending consolidated news to user {}", userPref.getUserId(), e);
            }
        } else {
            logger.debug("No new articles to send to user {}", userPref.getUserId());
        }
    }

    private void sendConsolidatedEmail(UserPreference userPref, 
                                      Map<String, List<NewsArticle>> articlesByCategory,
                                      List<String> userCategories,
                                      List<String> articleContentHashes,
                                      int totalNewArticles) {
        Notification notification = new Notification();
        notification.setUserId(userPref.getUserId());
        notification.setSubject("📰 Your Daily News Update - " + totalNewArticles + " New Articles");
        
        // Store all content hashes concatenated (for tracking purposes)
        notification.setArticleContentHash(String.join(",", articleContentHashes));
        
        // Set channel
        notification.setChannels(Set.of(Notification.NotificationChannel.EMAIL));
        
        // Build consolidated message with all articles grouped by category
        StringBuilder message = new StringBuilder();
        message.append("Hello! Here's your personalized news update:\n\n");
        message.append("═══════════════════════════════════════\n\n");
        
        // Iterate through categories in order
        for (String category : userCategories) {
            List<NewsArticle> categoryArticles = articlesByCategory.get(category);
            
            if (categoryArticles != null && !categoryArticles.isEmpty()) {
                message.append("📌 ").append(category.toUpperCase()).append("\n");
                message.append("─────────────────────────────────────\n\n");
                
                for (NewsArticle article : categoryArticles) {
                    message.append("📰 ").append(article.getTitle()).append("\n");
                    if (article.getDescription() != null && !article.getDescription().isEmpty()) {
                        message.append(article.getDescription()).append("\n");
                    }
                    message.append("🔗 ").append(article.getLink()).append("\n");
                    message.append("📅 ").append(article.getSource()).append("\n\n");
                }
                
                message.append("\n");
            }
        }
        
        message.append("═══════════════════════════════════════\n");
        message.append("Stay informed with Notified!");
        
        notification.setMessage(message.toString());
        
        // Send email only
        try {
            channelService.sendEmailNotification(userPref, notification);
            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Failed to send consolidated email to user {}", userPref.getUserId(), e);
            notification.setStatus(Notification.NotificationStatus.FAILED);
        }
        
        // Save the consolidated notification to user_notifications table
        notificationRepository.save(notification);

        // Additionally, record one entry per article for duplicate prevention across channels
        for (String category : userCategories) {
            List<NewsArticle> categoryArticles = articlesByCategory.get(category);
            if (categoryArticles == null || categoryArticles.isEmpty()) continue;

            for (NewsArticle article : categoryArticles) {
                Notification perArticle = new Notification();
                perArticle.setUserId(userPref.getUserId());
                perArticle.setSubject("📰 " + category + " News: " + article.getTitle());
                perArticle.setArticleContentHash(article.getContentHash());
                perArticle.setChannels(Set.of(Notification.NotificationChannel.EMAIL));
                perArticle.setMessage("Tracked in consolidated email. Link: " + article.getLink());
                perArticle.setStatus(Notification.NotificationStatus.SENT);
                perArticle.setSentAt(LocalDateTime.now());
                notificationRepository.save(perArticle);
            }
        }
        
        logger.info("Sent consolidated email with {} articles to user {}", 
            totalNewArticles, userPref.getUserId());
    }

    private void sendIndividualTelegramMessages(UserPreference userPref,
                                                Map<String, List<NewsArticle>> articlesByCategory,
                                                List<String> userCategories) {
        // Send each article as a separate Telegram message
        for (String category : userCategories) {
            List<NewsArticle> categoryArticles = articlesByCategory.get(category);
            
            if (categoryArticles != null && !categoryArticles.isEmpty()) {
                for (NewsArticle article : categoryArticles) {
                    Notification notification = new Notification();
                    notification.setUserId(userPref.getUserId());
                    notification.setSubject("📰 " + category + " News: " + article.getTitle());
                    notification.setArticleContentHash(article.getContentHash());
                    
                    // Set channel
                    notification.setChannels(Set.of(Notification.NotificationChannel.TELEGRAM));
                    
                    StringBuilder message = new StringBuilder();
                    message.append("📌 ").append(category.toUpperCase()).append("\n\n");
                    message.append("📰 ").append(article.getTitle()).append("\n\n");
                    if (article.getDescription() != null && !article.getDescription().isEmpty()) {
                        message.append(article.getDescription()).append("\n\n");
                    }
                    message.append("🔗 ").append(article.getLink()).append("\n");
                    message.append("📅 Source: ").append(article.getSource());
                    
                    notification.setMessage(message.toString());
                    
                    try {
                        // Send Telegram only
                        channelService.sendTelegramNotification(userPref, notification);
                        notification.setStatus(Notification.NotificationStatus.SENT);
                        notification.setSentAt(LocalDateTime.now());
                        
                        logger.debug("Sent Telegram message for article '{}' to user {}", 
                            article.getTitle(), userPref.getUserId());
                        
                    } catch (Exception e) {
                        logger.error("Failed to send Telegram message for article '{}' to user {}", 
                            article.getTitle(), userPref.getUserId(), e);
                        notification.setStatus(Notification.NotificationStatus.FAILED);
                    }
                    
                    // Save individual notification to user_notifications table (success or failure)
                    notificationRepository.save(notification);
                }
            }
        }
        
        logger.info("Sent {} individual Telegram messages to user {}", 
            articlesByCategory.values().stream().mapToInt(List::size).sum(), userPref.getUserId());
    }

    // Manual trigger for sending news (can be called via API)
    public void sendNewsToUserNow(String userId) {
        try {
            UserPreference userPref = preferenceClient.getUserPreference(userId);
            sendNewsToUser(userPref);
        } catch (Exception e) {
            logger.error("Error manually sending news to user: {}", userId, e);
            throw new RuntimeException("Failed to send news to user", e);
        }
    }
}
