package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    private final UserPreferenceClient preferenceClient;
    private final NotificationService notificationService;

    // Sample messages for each category
    private static final Map<String, List<String>> CATEGORY_MESSAGES = new HashMap<>();

    static {
        CATEGORY_MESSAGES.put("SPORTS", Arrays.asList(
            "⚽ Breaking: Champions League match tonight at 8 PM!",
            "🏀 NBA Finals Update: Game 5 results are in!",
            "🎾 Wimbledon 2024: Quarter-finals schedule released"
        ));
        CATEGORY_MESSAGES.put("NEWS", Arrays.asList(
            "📰 Breaking News: Major policy announcement expected today",
            "🌍 World Update: G20 Summit concludes with historic agreement",
            "📢 Local News: City council approves new infrastructure project"
        ));
        CATEGORY_MESSAGES.put("WEATHER", Arrays.asList(
            "🌤️ Weather Alert: Clear skies expected this week",
            "🌧️ Rain Advisory: Carry an umbrella today!",
            "🌡️ Temperature Update: High of 28°C expected today"
        ));
        CATEGORY_MESSAGES.put("SHOPPING", Arrays.asList(
            "🛒 Flash Sale: 50% off on electronics - Today only!",
            "🎁 Your wishlist item is now on sale!",
            "📦 New arrivals: Check out the latest collection"
        ));
        CATEGORY_MESSAGES.put("FINANCE", Arrays.asList(
            "💰 Market Update: S&P 500 reaches new high",
            "📈 Your portfolio gained 2.5% today",
            "💳 Reminder: Credit card payment due in 3 days"
        ));
        CATEGORY_MESSAGES.put("ENTERTAINMENT", Arrays.asList(
            "🎬 New Release: Top movie of the week now streaming",
            "🎵 Concert Alert: Your favorite artist announced new tour dates",
            "📺 Trending: New series everyone is talking about"
        ));
        CATEGORY_MESSAGES.put("HEALTH", Arrays.asList(
            "🏥 Health Tip: Stay hydrated - drink 8 glasses of water today",
            "💊 Reminder: Time for your daily wellness check",
            "🏃 Fitness Goal: You're 80% to your weekly step target!"
        ));
        CATEGORY_MESSAGES.put("TECHNOLOGY", Arrays.asList(
            "💻 Tech News: New smartphone announced with revolutionary features",
            "🔧 Software Update: Important security patch available",
            "🚀 Innovation: AI breakthrough in medical diagnosis"
        ));
        CATEGORY_MESSAGES.put("TRAVEL", Arrays.asList(
            "✈️ Travel Deal: 40% off flights to popular destinations",
            "🏨 Hotel Alert: Last-minute deals in your saved destinations",
            "🗺️ Trending: Top 10 travel destinations for 2024"
        ));
        CATEGORY_MESSAGES.put("SOCIAL", Arrays.asList(
            "👥 Friend Update: 5 friends posted new updates",
            "🎉 Event Reminder: Birthday party tomorrow at 7 PM",
            "💬 New message from your group chat"
        ));
        CATEGORY_MESSAGES.put("EDUCATION", Arrays.asList(
            "📚 Course Update: New lesson available in your enrolled course",
            "🎓 Learning Tip: Spend 15 minutes on skill development today",
            "📝 Quiz Reminder: Weekly assessment due tomorrow"
        ));
        CATEGORY_MESSAGES.put("PROMOTIONS", Arrays.asList(
            "🎁 Exclusive Offer: Use code SAVE20 for 20% off",
            "🏷️ Limited Time: Buy 2 Get 1 Free on selected items",
            "⭐ VIP Access: Early bird sale starts now for members"
        ));
    }

    public AutoNotificationService(UserPreferenceClient preferenceClient, 
                                   NotificationService notificationService) {
        this.preferenceClient = preferenceClient;
        this.notificationService = notificationService;
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

            logger.info("Found {} registered users. Sending personalized notifications...", allPreferences.size());
            
            Random random = new Random();
            int successCount = 0;
            int failCount = 0;

            for (UserPreference pref : allPreferences) {
                try {
                    List<String> userPreferences = pref.getPreferences();
                    
                    if (userPreferences == null || userPreferences.isEmpty()) {
                        logger.debug("User {} has no category preferences set, skipping", pref.getUserId());
                        continue;
                    }

                    // Pick a random category from user's preferences
                    String randomCategory = userPreferences.get(random.nextInt(userPreferences.size()));
                    
                    // Get a random message for that category
                    List<String> messages = CATEGORY_MESSAGES.get(randomCategory.toUpperCase());
                    if (messages == null || messages.isEmpty()) {
                        logger.debug("No messages found for category: {}", randomCategory);
                        continue;
                    }
                    
                    String message = messages.get(random.nextInt(messages.size()));
                    
                    // Create and send notification
                    Notification notification = new Notification();
                    notification.setUserId(pref.getUserId());
                    notification.setSubject("📬 " + randomCategory + " Update");
                    notification.setMessage(message + "\n\n📅 " + 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
                    
                    notificationService.sendNotification(notification);
                    
                    logger.info("✅ Sent {} notification to user: {}", randomCategory, pref.getUserId());
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
}
