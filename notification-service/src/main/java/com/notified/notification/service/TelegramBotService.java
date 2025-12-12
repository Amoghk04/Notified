package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import com.notified.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TelegramBotService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot-token:}")
    private String telegramBotToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserPreferenceClient preferenceClient;
    private final NotificationRepository notificationRepository;
    private final NewsScraperService newsScraperService;
    
    // Store user states for multi-step flows
    private final Map<String, UserRegistrationState> userStates = new ConcurrentHashMap<>();
    
    // Category emojis
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
    
    // Available category commands
    private static final Set<String> CATEGORY_COMMANDS = Set.of(
        "sports", "news", "weather", "shopping", "finance", 
        "entertainment", "health", "technology", "travel", 
        "social", "education", "promotions"
    );
    
    private static final int ARTICLES_PER_PAGE = 5;

    public TelegramBotService(UserPreferenceClient preferenceClient, 
                              NotificationRepository notificationRepository,
                              NewsScraperService newsScraperService) {
        this.preferenceClient = preferenceClient;
        this.notificationRepository = notificationRepository;
        this.newsScraperService = newsScraperService;
    }
    
    /**
     * Register bot commands with Telegram for autofill/autocomplete
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerBotCommands() {
        String token = getToken();
        if (token == null) {
            logger.warn("Cannot register bot commands - no token configured");
            return;
        }
        
        try {
            String url = "https://api.telegram.org/bot" + token + "/setMyCommands";
            
            // Build commands JSON
            List<Map<String, String>> commands = new ArrayList<>();
            commands.add(Map.of("command", "start", "description", "👋 Welcome message"));
            commands.add(Map.of("command", "register", "description", "📝 Register new account"));
            commands.add(Map.of("command", "updatepref", "description", "⚙️ Update your preferences"));
            commands.add(Map.of("command", "frequency", "description", "⏰ Change notification frequency"));
            commands.add(Map.of("command", "status", "description", "📊 View your current settings"));
            commands.add(Map.of("command", "history", "description", "📜 View notification history"));
            commands.add(Map.of("command", "browse", "description", "📚 Browse news by category"));
            commands.add(Map.of("command", "sports", "description", "⚽ Browse Sports news"));
            commands.add(Map.of("command", "news", "description", "📰 Browse General news"));
            commands.add(Map.of("command", "technology", "description", "💻 Browse Technology news"));
            commands.add(Map.of("command", "entertainment", "description", "🎬 Browse Entertainment news"));
            commands.add(Map.of("command", "finance", "description", "💰 Browse Finance news"));
            commands.add(Map.of("command", "health", "description", "🏥 Browse Health news"));
            commands.add(Map.of("command", "weather", "description", "🌤️ Browse Weather news"));
            commands.add(Map.of("command", "travel", "description", "✈️ Browse Travel news"));
            commands.add(Map.of("command", "education", "description", "📚 Browse Education news"));
            commands.add(Map.of("command", "social", "description", "👥 Browse Social news"));
            commands.add(Map.of("command", "shopping", "description", "🛒 Browse Shopping news"));
            commands.add(Map.of("command", "promotions", "description", "🎁 Browse Promotions"));
            commands.add(Map.of("command", "help", "description", "❓ Show help message"));
            
            Map<String, Object> body = new HashMap<>();
            body.put("commands", commands);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Successfully registered bot commands with Telegram for autofill");
            } else {
                logger.warn("Failed to register bot commands: {}", response.getBody());
            }
        } catch (Exception e) {
            logger.error("Error registering bot commands with Telegram: {}", e.getMessage());
        }
    }

    private static class UserRegistrationState {
        String userId;
        String email;
        Set<String> selectedCategories = new HashSet<>();
        boolean waitingForUserId;
        boolean waitingForEmailChoice;  // Ask if they want email notifications
        boolean waitingForEmail;        // Ask for actual email address
        boolean waitingForCategories;
        boolean waitingForFrequency;    // Ask for notification frequency
        boolean wantsEmail;             // Whether user wants email notifications
        Integer notificationIntervalMinutes = 60;  // Default: 1 hour
    }
    
    // Notification frequency options in minutes
    private static final Map<String, Integer> FREQUENCY_OPTIONS = new LinkedHashMap<>();
    static {
        FREQUENCY_OPTIONS.put("1", 1);           // Every minute (for testing)
        FREQUENCY_OPTIONS.put("2", 720);         // Every 12 hours
        FREQUENCY_OPTIONS.put("3", 1440);        // Every 24 hours (daily)
        FREQUENCY_OPTIONS.put("4", 2880);        // Every 48 hours
        FREQUENCY_OPTIONS.put("5", 10080);       // Every week
    }

    public void processUpdate(Map<String, Object> update) {
        try {
            // Handle callback queries (button presses)
            Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
            if (callbackQuery != null) {
                handleCallbackQuery(callbackQuery);
                return;
            }

            // Extract message from update
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message == null) {
                logger.debug("No message in update, skipping");
                return;
            }

            // Extract chat info
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            Map<String, Object> from = (Map<String, Object>) message.get("from");
            String text = (String) message.get("text");

            if (chat == null || from == null) {
                logger.warn("Missing chat or from data in message");
                return;
            }

            Object chatIdObj = chat.get("id");
            String chatId = chatIdObj != null ? chatIdObj.toString() : null;
            String firstName = (String) from.get("first_name");
            String username = (String) from.get("username");

            logger.info("Received message from chatId={} firstName={} username={} text={}", 
                chatId, firstName, username, text);

            if (chatId == null) {
                logger.warn("No chat ID in message");
                return;
            }

            if (text == null) {
                return;
            }

            // Check if user is in a registration flow
            UserRegistrationState state = userStates.get(chatId);
            if (state != null) {
                handleRegistrationFlow(chatId, text, state);
                return;
            }

            // Handle commands
            if (text.equals("/")) {
                // Show command menu when just "/" is typed
                showCommandMenu(chatId);
            } else if (text.startsWith("/start")) {
                handleStartCommand(chatId, firstName, username);
            } else if (text.startsWith("/register")) {
                handleRegisterCommand(chatId);
            } else if (text.startsWith("/history")) {
                handleHistoryCommand(chatId);
            } else if (text.startsWith("/updatepref") || text.startsWith("/update")) {
                handleUpdatePreferencesCommand(chatId);
            } else if (text.startsWith("/frequency")) {
                handleFrequencyCommand(chatId);
            } else if (text.startsWith("/status")) {
                handleStatusCommand(chatId);
            } else if (text.startsWith("/help")) {
                handleHelpCommand(chatId);
            } else if (text.startsWith("/refreshcommands")) {
                // Admin command to re-register bot commands
                registerBotCommands();
                sendTelegramMessage(chatId, "✅ Bot commands refreshed! Type / to see them.");
            } else if (text.startsWith("/browse")) {
                // Show all category options
                handleBrowseCommand(chatId);
            } else if (isCategoryCommand(text)) {
                // Handle category commands like /sports, /news, etc.
                String category = text.substring(1).toLowerCase().trim();
                handleCategoryBrowseCommand(chatId, category, 0);
            } else {
                sendTelegramMessage(chatId, "Unknown command. Type / to see all available commands.");
            }

        } catch (Exception e) {
            logger.error("Error processing Telegram update", e);
        }
    }

    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        try {
            String queryId = (String) callbackQuery.get("id");
            String data = (String) callbackQuery.get("data");
            Map<String, Object> from = (Map<String, Object>) callbackQuery.get("from");
            Map<String, Object> message = (Map<String, Object>) callbackQuery.get("message");
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            String chatId = chat.get("id").toString();

            logger.info("Received callback query from chatId={} data={}", chatId, data);

            // Handle reaction callbacks (like/dislike on news articles)
            if (data.startsWith("reaction_like_") || data.startsWith("reaction_dislike_")) {
                handleReactionCallback(queryId, data, chatId, message);
                return;
            }
            
            // Handle pagination callbacks (page_CATEGORY_PAGENUMBER)
            if (data.startsWith("page_")) {
                handlePaginationCallback(queryId, data, chatId, message);
                return;
            }

            UserRegistrationState state = userStates.get(chatId);
            if (state == null) {
                answerCallbackQuery(queryId, "Session expired. Please start again with /register");
                return;
            }

            if (data.startsWith("cat_")) {
                String category = data.substring(4);
                if (state.selectedCategories.contains(category)) {
                    state.selectedCategories.remove(category);
                    answerCallbackQuery(queryId, "❌ " + category + " removed");
                } else {
                    state.selectedCategories.add(category);
                    answerCallbackQuery(queryId, "✅ " + category + " added");
                }
            } else if (data.equals("done_categories")) {
                answerCallbackQuery(queryId, "Preferences saved!");
                finishRegistration(chatId, state);
            }

        } catch (Exception e) {
            logger.error("Error handling callback query", e);
        }
    }

    private void handleReactionCallback(String queryId, String data, String chatId, Map<String, Object> message) {
        try {
            String reaction;
            String notificationId;
            
            if (data.startsWith("reaction_like_")) {
                reaction = "like";
                notificationId = data.substring("reaction_like_".length());
            } else {
                reaction = "dislike";
                notificationId = data.substring("reaction_dislike_".length());
            }
            
            // Find the notification and update reaction
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            
            if (notificationOpt.isPresent()) {
                Notification notification = notificationOpt.get();
                String previousReaction = notification.getUserReaction();
                
                // Toggle: if same reaction clicked again, remove it
                if (reaction.equals(previousReaction)) {
                    notification.setUserReaction(null);
                    notificationRepository.save(notification);
                    answerCallbackQuery(queryId, "Reaction removed");
                    updateMessageButtons(chatId, message, null, notificationId);
                } else {
                    notification.setUserReaction(reaction);
                    notificationRepository.save(notification);
                    
                    String emoji = reaction.equals("like") ? "👍" : "👎";
                    answerCallbackQuery(queryId, emoji + " Thanks for your feedback!");
                    updateMessageButtons(chatId, message, reaction, notificationId);
                }
                
                logger.info("User {} reacted '{}' to notification {}", chatId, reaction, notificationId);
            } else {
                answerCallbackQuery(queryId, "Article not found");
                logger.warn("Notification not found for reaction: {}", notificationId);
            }
            
        } catch (Exception e) {
            logger.error("Error handling reaction callback: {}", e.getMessage());
            answerCallbackQuery(queryId, "Error processing reaction");
        }
    }

    private void updateMessageButtons(String chatId, Map<String, Object> message, String currentReaction, String notificationId) {
        try {
            String token = getToken();
            if (token == null) return;
            
            Object messageIdObj = message.get("message_id");
            if (messageIdObj == null) return;
            
            String url = "https://api.telegram.org/bot" + token + "/editMessageReplyMarkup";
            
            // Build updated inline keyboard with selected reaction highlighted
            List<Map<String, String>> row = new ArrayList<>();
            
            String likeText = "like".equals(currentReaction) ? "👍 Liked ✓" : "👍 Like";
            String dislikeText = "dislike".equals(currentReaction) ? "👎 Disliked ✓" : "👎 Dislike";
            
            row.add(Map.of("text", likeText, "callback_data", "reaction_like_" + notificationId));
            row.add(Map.of("text", dislikeText, "callback_data", "reaction_dislike_" + notificationId));
            
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(row);
            
            Map<String, Object> inlineKeyboard = new HashMap<>();
            inlineKeyboard.put("inline_keyboard", keyboard);
            
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageIdObj);
            body.put("reply_markup", inlineKeyboard);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            
            restTemplate.postForEntity(url, entity, String.class);
            
        } catch (Exception e) {
            logger.error("Error updating message buttons: {}", e.getMessage());
        }
    }

    private void answerCallbackQuery(String queryId, String text) {
        try {
            String token = getToken();
            if (token == null) return;

            String url = "https://api.telegram.org/bot" + token + "/answerCallbackQuery";
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("callback_query_id", queryId);
            params.add("text", text);

            restTemplate.postForEntity(url, params, String.class);
        } catch (Exception e) {
            logger.error("Failed to answer callback query", e);
        }
    }

    private void handleRegistrationFlow(String chatId, String text, UserRegistrationState state) {
        if (state.waitingForUserId) {
            state.userId = text.trim();
            state.waitingForUserId = false;
            state.waitingForEmailChoice = true;
            sendTelegramMessage(chatId, 
                "📧 Would you like to receive notifications via email?\n\n" +
                "Type YES or NO:");
        } else if (state.waitingForEmailChoice) {
            String input = text.trim().toUpperCase();
            if (input.equals("YES") || input.equals("Y")) {
                state.wantsEmail = true;
                state.waitingForEmailChoice = false;
                state.waitingForEmail = true;
                sendTelegramMessage(chatId, "Great! Please enter your email address:");
            } else if (input.equals("NO") || input.equals("N")) {
                state.wantsEmail = false;
                state.email = null;  // No email
                state.waitingForEmailChoice = false;
                state.waitingForCategories = true;
                sendTelegramMessage(chatId, "👍 No problem! You'll receive notifications via Telegram only.");
                // Send the numbered category list
                sendCategoryList(chatId, state.selectedCategories);
            } else {
                sendTelegramMessage(chatId, "⚠️ Please type YES or NO:");
            }
        } else if (state.waitingForEmail) {
            state.email = text.trim();
            state.waitingForEmail = false;
            state.waitingForCategories = true;
            // Send the numbered category list
            sendCategoryList(chatId, state.selectedCategories);
        } else if (state.waitingForCategories) {
            String input = text.trim();
            String upperInput = input.toUpperCase();
            
            if (upperInput.equals("DONE")) {
                // Move to frequency selection
                state.waitingForCategories = false;
                state.waitingForFrequency = true;
                sendFrequencyOptions(chatId);
            } else {
                // Try to parse as a number first
                String[] categories = {"SPORTS", "NEWS", "WEATHER", "SHOPPING", "FINANCE", 
                                       "ENTERTAINMENT", "HEALTH", "TECHNOLOGY", "TRAVEL", 
                                       "SOCIAL", "EDUCATION", "PROMOTIONS"};
                
                try {
                    int num = Integer.parseInt(input.trim());
                    if (num >= 1 && num <= categories.length) {
                        String category = categories[num - 1];
                        if (state.selectedCategories.contains(category)) {
                            state.selectedCategories.remove(category);
                            sendTelegramMessage(chatId, "❌ Removed: " + category);
                        } else {
                            state.selectedCategories.add(category);
                            sendTelegramMessage(chatId, "✅ Added: " + category);
                        }
                        // Show updated selection
                        sendCurrentSelection(chatId, state.selectedCategories);
                    } else {
                        sendTelegramMessage(chatId, "⚠️ Please enter a number between 1 and " + categories.length + 
                            "\nor type a custom topic name.");
                    }
                } catch (NumberFormatException e) {
                    // Not a number - treat as custom topic
                    if (input.length() >= 2 && input.length() <= 30) {
                        String customTopic = input.toUpperCase().replaceAll("[^A-Z0-9_ ]", "").trim();
                        if (!customTopic.isEmpty()) {
                            if (state.selectedCategories.contains(customTopic)) {
                                state.selectedCategories.remove(customTopic);
                                sendTelegramMessage(chatId, "❌ Removed custom topic: " + customTopic);
                            } else {
                                state.selectedCategories.add(customTopic);
                                sendTelegramMessage(chatId, "✅ Added custom topic: " + customTopic);
                            }
                            sendCurrentSelection(chatId, state.selectedCategories);
                        } else {
                            sendTelegramMessage(chatId, "⚠️ Invalid topic name. Please use letters and numbers only.");
                        }
                    } else {
                        sendTelegramMessage(chatId, 
                            "⚠️ Enter a number (1-12) for preset categories,\n" +
                            "or type a custom topic (2-30 characters),\n" +
                            "or type DONE to finish.");
                    }
                }
            }
        } else if (state.waitingForFrequency) {
            String input = text.trim();
            
            if (FREQUENCY_OPTIONS.containsKey(input)) {
                state.notificationIntervalMinutes = FREQUENCY_OPTIONS.get(input);
                state.waitingForFrequency = false;
                finishRegistration(chatId, state);
            } else {
                sendTelegramMessage(chatId, "⚠️ Please enter a number from 1 to 5:");
                sendFrequencyOptions(chatId);
            }
        }
    }
    
    private void sendFrequencyOptions(String chatId) {
        StringBuilder msg = new StringBuilder();
        msg.append("⏰ *How often do you want to receive notifications?*\n\n");
        msg.append("1️⃣ - Every minute (for testing)\n");
        msg.append("2️⃣ - Every 12 hours\n");
        msg.append("3️⃣ - Every 24 hours (daily)\n");
        msg.append("4️⃣ - Every 48 hours\n");
        msg.append("5️⃣ - Every week\n\n");
        msg.append("Type a number (1-5):");
        
        sendTelegramMessage(chatId, msg.toString());
    }
    
    private String getFrequencyText(int minutes) {
        if (minutes <= 1) return "Every minute";
        if (minutes == 720) return "Every 12 hours";
        if (minutes == 1440) return "Every 24 hours (daily)";
        if (minutes == 2880) return "Every 48 hours";
        if (minutes == 10080) return "Every week";
        // For custom values
        if (minutes < 60) return "Every " + minutes + " minutes";
        if (minutes < 1440) return "Every " + (minutes / 60) + " hours";
        return "Every " + (minutes / 1440) + " days";
    }

    private void sendCategoryList(String chatId, Set<String> selectedCategories) {
        String[] categories = {"SPORTS", "NEWS", "WEATHER", "SHOPPING", "FINANCE", 
                               "ENTERTAINMENT", "HEALTH", "TECHNOLOGY", "TRAVEL", 
                               "SOCIAL", "EDUCATION", "PROMOTIONS"};
        
        Map<String, String> categoryEmojis = Map.ofEntries(
            Map.entry("SPORTS", "⚽"), Map.entry("NEWS", "📰"), Map.entry("WEATHER", "🌤️"),
            Map.entry("SHOPPING", "🛒"), Map.entry("FINANCE", "💰"), Map.entry("ENTERTAINMENT", "🎬"),
            Map.entry("HEALTH", "🏥"), Map.entry("TECHNOLOGY", "💻"), Map.entry("TRAVEL", "✈️"),
            Map.entry("SOCIAL", "👥"), Map.entry("EDUCATION", "📚"), Map.entry("PROMOTIONS", "🎁")
        );

        StringBuilder msg = new StringBuilder();
        msg.append("📋 *Select Your Notification Categories*\n\n");
        msg.append("Type a number to add/remove a category:\n\n");
        
        for (int i = 0; i < categories.length; i++) {
            String cat = categories[i];
            String mark = selectedCategories.contains(cat) ? "✅" : "⬜";
            msg.append(i + 1).append(" - ").append(mark).append(" ")
               .append(categoryEmojis.getOrDefault(cat, "📌"))
               .append(" ").append(cat).append("\n");
        }
        
        msg.append("\n━━━━━━━━━━━━━━━━\n");
        msg.append("📝 Type a number (1-12) to toggle\n");
        msg.append("✏️ Or type any custom topic (e.g., Cricket, Bollywood, IPL)\n");
        msg.append("✅ Type DONE when finished");

        sendTelegramMessage(chatId, msg.toString());
    }

    private void sendCurrentSelection(String chatId, Set<String> selectedCategories) {
        // Separate preset categories from custom topics
        String[] presetCategories = {"SPORTS", "NEWS", "WEATHER", "SHOPPING", "FINANCE", 
                                     "ENTERTAINMENT", "HEALTH", "TECHNOLOGY", "TRAVEL", 
                                     "SOCIAL", "EDUCATION", "PROMOTIONS"};
        Set<String> presetSet = new java.util.HashSet<>(Arrays.asList(presetCategories));
        
        List<String> presets = new ArrayList<>();
        List<String> custom = new ArrayList<>();
        
        for (String cat : selectedCategories) {
            if (presetSet.contains(cat)) {
                presets.add(cat);
            } else {
                custom.add(cat);
            }
        }
        
        if (selectedCategories.isEmpty()) {
            sendTelegramMessage(chatId, 
                "📊 Selected: None\n\n" +
                "Type a number, custom topic, or DONE to finish.");
        } else {
            StringBuilder msg = new StringBuilder("📊 *Your Selection:*\n");
            if (!presets.isEmpty()) {
                msg.append("📌 Categories: ").append(String.join(", ", presets)).append("\n");
            }
            if (!custom.isEmpty()) {
                msg.append("✏️ Custom: ").append(String.join(", ", custom)).append("\n");
            }
            msg.append("\nType another number/topic to add/remove, or DONE to finish.");
            sendTelegramMessage(chatId, msg.toString());
        }
    }

    private void finishRegistration(String chatId, UserRegistrationState state) {
        try {
            // Create or update user preference
            UserPreference pref = new UserPreference();
            pref.setUserId(state.userId);
            pref.setTelegramChatId(chatId);
            
            // Set enabled channels based on user choices
            Set<UserPreference.NotificationChannel> channels = new HashSet<>();
            channels.add(UserPreference.NotificationChannel.TELEGRAM);
            
            // Only add email channel if user wants email notifications
            if (state.wantsEmail && state.email != null && !state.email.isEmpty()) {
                pref.setEmail(state.email);
                channels.add(UserPreference.NotificationChannel.EMAIL);
            }
            pref.setEnabledChannels(channels);
            
            // Set selected preferences (categories) as a List
            List<String> preferencesList = new ArrayList<>(state.selectedCategories);
            pref.setPreferences(preferencesList);
            
            // Set notification frequency
            pref.setNotificationIntervalMinutes(state.notificationIntervalMinutes);

            // Update via API with proper Content-Type header
            String apiUrl = "http://localhost:8081/preferences";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserPreference> entity = new HttpEntity<>(pref, headers);
            
            try {
                // Try to update existing using exchange() which supports HttpEntity with headers
                restTemplate.exchange(
                    apiUrl + "/" + state.userId, 
                    org.springframework.http.HttpMethod.PUT, 
                    entity, 
                    UserPreference.class
                );
                logger.info("Updated preferences for user: {}", state.userId);
            } catch (Exception e) {
                // Create new if doesn't exist
                restTemplate.postForEntity(apiUrl, entity, UserPreference.class);
                logger.info("Created new preferences for user: {}", state.userId);
            }

            String emailStatus = state.wantsEmail ? state.email : "Not enabled";
            String frequencyText = getFrequencyText(state.notificationIntervalMinutes);
            String successMsg = String.format(
                "🎉 Registration complete!\n\n" +
                "📋 Your Details:\n" +
                "👤 User ID: %s\n" +
                "📧 Email: %s\n" +
                "📱 Telegram: Connected ✅\n" +
                "🔔 Preferences: %s\n" +
                "⏰ Frequency: %s\n\n" +
                "You'll now receive notifications for your selected topics!\n\n" +
                "📝 Commands:\n" +
                "/updatepref - Update your preferences\n" +
                "/frequency - Change notification frequency\n" +
                "/status - View your current settings\n" +
                "/history - View notification history\n" +
                "/help - Show help\n\n" +
                "💡 Type / to see all commands!",
                state.userId,
                emailStatus,
                state.selectedCategories.isEmpty() ? "None selected" : String.join(", ", state.selectedCategories),
                frequencyText
            );

            sendTelegramMessage(chatId, successMsg);
            userStates.remove(chatId);

        } catch (Exception e) {
            logger.error("Failed to finish registration for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Sorry, registration failed. Please try again with /register");
            userStates.remove(chatId);
        }
    }

    private void handleStartCommand(String chatId, String firstName, String username) {
        try {
            String welcomeMsg = String.format(
                "👋 Welcome to Notified, %s!\n\n" +
                "I'll help you manage your notification preferences.\n\n" +
                "📋 Available Commands:\n" +
                "/register - Register and set your preferences\n" +
                "/updatepref - Update your preferences\n" +
                "/frequency - Change notification frequency\n" +
                "/status - View your current settings\n" +
                "/history - View notification history\n" +
                "/help - Show detailed help\n\n" +
                "💡 Type / to see all commands with autofill!\n\n" +
                "Start by using /register to set up your account!",
                firstName != null ? firstName : "friend"
            );
            
            sendTelegramMessage(chatId, welcomeMsg);
            logger.info("Handled /start for chatId={}", chatId);

        } catch (Exception e) {
            logger.error("Error handling /start command for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "Sorry, something went wrong. Please try again later.");
        }
    }

    private void handleRegisterCommand(String chatId) {
        UserRegistrationState state = new UserRegistrationState();
        state.waitingForUserId = true;
        userStates.put(chatId, state);
        
        sendTelegramMessage(chatId, 
            "📝 Let's get you registered!\n\n" +
            "Please enter your User ID (this can be your name, email, or any unique identifier):");
    }

    private void handleHistoryCommand(String chatId) {
        try {
            // Find user by telegram chat ID
            String apiUrl = "http://localhost:8081/preferences";
            UserPreference[] allPrefs = restTemplate.getForObject(apiUrl, UserPreference[].class);
            
            UserPreference userPref = null;
            if (allPrefs != null) {
                for (UserPreference pref : allPrefs) {
                    if (chatId.equals(pref.getTelegramChatId())) {
                        userPref = pref;
                        break;
                    }
                }
            }

            if (userPref == null) {
                sendTelegramMessage(chatId, "You're not registered yet. Use /register to get started!");
                return;
            }

            // Get notifications for this user
            List<Notification> notifications = notificationRepository.findByUserId(userPref.getUserId());
            
            if (notifications.isEmpty()) {
                sendTelegramMessage(chatId, "📭 No notifications yet!");
                return;
            }

            StringBuilder msg = new StringBuilder("📬 Your Notification History:\n\n");
            int count = 0;
            for (Notification notif : notifications) {
                if (count++ >= 10) break; // Limit to 10 most recent
                msg.append("━━━━━━━━━━━━━━━━\n");
                msg.append("📌 ").append(notif.getSubject() != null ? notif.getSubject() : "No Subject").append("\n");
                msg.append("💬 ").append(notif.getMessage()).append("\n");
                msg.append("📊 Status: ").append(notif.getStatus()).append("\n");
                if (notif.getSentAt() != null) {
                    msg.append("📅 ").append(notif.getSentAt()).append("\n");
                }
            }

            sendTelegramMessage(chatId, msg.toString());

        } catch (Exception e) {
            logger.error("Error handling /history for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Failed to load history. Please try again later.");
        }
    }

    private void handleUpdatePreferencesCommand(String chatId) {
        try {
            // Find user by telegram chat ID
            String apiUrl = "http://localhost:8081/preferences";
            UserPreference[] allPrefs = restTemplate.getForObject(apiUrl, UserPreference[].class);
            
            UserPreference userPref = null;
            if (allPrefs != null) {
                for (UserPreference pref : allPrefs) {
                    if (chatId.equals(pref.getTelegramChatId())) {
                        userPref = pref;
                        break;
                    }
                }
            }

            if (userPref == null) {
                sendTelegramMessage(chatId, "You're not registered yet. Use /register to get started!");
                return;
            }

            // Start update flow with existing preferences loaded
            UserRegistrationState state = new UserRegistrationState();
            state.userId = userPref.getUserId();
            state.email = userPref.getEmail();
            state.wantsEmail = userPref.isEmailEnabled();
            state.notificationIntervalMinutes = userPref.getNotificationIntervalMinutes();
            // Load existing categories
            if (userPref.getPreferences() != null) {
                state.selectedCategories = new HashSet<>(userPref.getPreferences());
            }
            state.waitingForCategories = true;
            userStates.put(chatId, state);

            String currentPrefs = state.selectedCategories.isEmpty() ? "None" : String.join(", ", state.selectedCategories);
            sendTelegramMessage(chatId, 
                "⚙️ *Update Your Preferences*\n\n" +
                "📌 Current categories: " + currentPrefs + "\n" +
                "⏰ Current frequency: " + getFrequencyText(state.notificationIntervalMinutes) + "\n\n" +
                "Select categories to add/remove:");
            sendCategoryList(chatId, state.selectedCategories);

        } catch (Exception e) {
            logger.error("Error handling /updatepref for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Failed to start update. Please try again later.");
        }
    }

    private void handleHelpCommand(String chatId) {
        String helpMsg = 
            "🤖 Notified Bot Help\n\n" +
            "📋 Available Commands:\n\n" +
            "/start - Welcome message\n" +
            "/register - Register and set preferences\n" +
            "/updatepref - Update your preferences (categories, email, frequency)\n" +
            "/frequency - Change notification frequency\n" +
            "/status - View your current settings\n" +
            "/history - View notification history\n" +
            "/help - Show this help message\n\n" +
            "💡 Tip: Type / to see all commands with autofill!";
        
        sendTelegramMessage(chatId, helpMsg);
    }
    
    private void showCommandMenu(String chatId) {
        String menuMsg = 
            "📋 *Available Commands:*\n\n" +
            "🚀 /start - Welcome message\n" +
            "📝 /register - Register new account\n" +
            "⚙️ /updatepref - Update your preferences\n" +
            "⏰ /frequency - Change notification frequency\n" +
            "📊 /status - View your current settings\n" +
            "📜 /history - View notification history\n" +
            "❓ /help - Show detailed help\n\n" +
            "Tap any command to use it!";
        
        sendTelegramMessage(chatId, menuMsg);
    }
    
    private void handleStatusCommand(String chatId) {
        try {
            // Find user by telegram chat ID
            String apiUrl = "http://localhost:8081/preferences";
            UserPreference[] allPrefs = restTemplate.getForObject(apiUrl, UserPreference[].class);
            
            UserPreference userPref = null;
            if (allPrefs != null) {
                for (UserPreference pref : allPrefs) {
                    if (chatId.equals(pref.getTelegramChatId())) {
                        userPref = pref;
                        break;
                    }
                }
            }

            if (userPref == null) {
                sendTelegramMessage(chatId, "You're not registered yet. Use /register to get started!");
                return;
            }

            String emailStatus = userPref.isEmailEnabled() ? userPref.getEmail() : "Not enabled";
            String frequencyText = getFrequencyText(userPref.getNotificationIntervalMinutes());
            List<String> prefs = userPref.getPreferences();
            String categories = (prefs == null || prefs.isEmpty()) ? "None selected" : String.join(", ", prefs);
            
            String lastNotif = "Never";
            if (userPref.getLastNotificationSent() != null) {
                lastNotif = userPref.getLastNotificationSent().toString();
            }

            String statusMsg = String.format(
                "📊 *Your Current Settings*\n\n" +
                "👤 User ID: %s\n" +
                "📧 Email: %s\n" +
                "📱 Telegram: Connected ✅\n" +
                "🔔 Categories: %s\n" +
                "⏰ Frequency: %s\n" +
                "📅 Last Notification: %s\n\n" +
                "Use /updatepref to change settings.",
                userPref.getUserId(),
                emailStatus,
                categories,
                frequencyText,
                lastNotif
            );

            sendTelegramMessage(chatId, statusMsg);

        } catch (Exception e) {
            logger.error("Error handling /status for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Failed to load status. Please try again later.");
        }
    }
    
    private void handleFrequencyCommand(String chatId) {
        try {
            // Find user by telegram chat ID
            String apiUrl = "http://localhost:8081/preferences";
            UserPreference[] allPrefs = restTemplate.getForObject(apiUrl, UserPreference[].class);
            
            UserPreference userPref = null;
            if (allPrefs != null) {
                for (UserPreference pref : allPrefs) {
                    if (chatId.equals(pref.getTelegramChatId())) {
                        userPref = pref;
                        break;
                    }
                }
            }

            if (userPref == null) {
                sendTelegramMessage(chatId, "You're not registered yet. Use /register to get started!");
                return;
            }

            // Start frequency update flow
            UserRegistrationState state = new UserRegistrationState();
            state.userId = userPref.getUserId();
            state.email = userPref.getEmail();
            state.selectedCategories = new HashSet<>(userPref.getPreferences() != null ? userPref.getPreferences() : new ArrayList<>());
            state.wantsEmail = userPref.isEmailEnabled();
            state.waitingForFrequency = true;
            userStates.put(chatId, state);
            
            String currentFrequency = getFrequencyText(userPref.getNotificationIntervalMinutes());
            sendTelegramMessage(chatId, "⏰ Your current notification frequency: " + currentFrequency + "\n\nLet's change it:");
            sendFrequencyOptions(chatId);

        } catch (Exception e) {
            logger.error("Error handling /frequency for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Failed to update frequency. Please try again later.");
        }
    }

    private String getToken() {
        String token = (telegramBotToken != null && !telegramBotToken.isBlank())
                ? telegramBotToken
                : System.getenv("TELEGRAM_BOT_TOKEN");
        
        if (token == null || token.isBlank()) {
            logger.warn("Telegram bot token not configured");
            return null;
        }
        return token;
    }

    private void sendTelegramMessage(String chatId, String text) {
        try {
            String token = getToken();
            if (token == null) return;

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("chat_id", chatId);
            params.add("text", text);

            ResponseEntity<String> response = restTemplate.postForEntity(url, params, String.class);
            logger.debug("Sent message to chatId={} response={}", chatId, response.getStatusCode());

        } catch (Exception e) {
            logger.error("Failed to send Telegram message to chatId={}", chatId, e);
        }
    }

    // ========== CATEGORY BROWSING METHODS ==========

    private boolean isCategoryCommand(String text) {
        if (!text.startsWith("/")) return false;
        String command = text.substring(1).toLowerCase().trim().split("\\s+")[0];
        return CATEGORY_COMMANDS.contains(command);
    }

    private void handleBrowseCommand(String chatId) {
        StringBuilder msg = new StringBuilder();
        msg.append("📚 *Browse News by Category*\n");
        msg.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        msg.append("Tap a category to browse articles:\n\n");
        
        for (String category : CATEGORY_COMMANDS) {
            String emoji = CATEGORY_EMOJIS.getOrDefault(category.toUpperCase(), "📌");
            msg.append(emoji).append(" /").append(category).append("\n");
        }
        
        msg.append("\n💡 Each category shows the latest articles with pagination.");
        
        sendTelegramMessage(chatId, msg.toString());
    }

    private void handleCategoryBrowseCommand(String chatId, String category, int page) {
        try {
            String upperCategory = category.toUpperCase();
            String emoji = CATEGORY_EMOJIS.getOrDefault(upperCategory, "📌");
            
            // Fetch articles for this page
            List<com.notified.notification.model.NewsArticle> articles = 
                newsScraperService.getArticlesByCategoryPaginated(category, page, ARTICLES_PER_PAGE);
            
            // Get total count for pagination
            long totalArticles = newsScraperService.countArticlesByCategory(category);
            int totalPages = (int) Math.ceil((double) totalArticles / ARTICLES_PER_PAGE);
            
            if (articles.isEmpty()) {
                sendTelegramMessage(chatId, emoji + " *" + upperCategory + "*\n\n" +
                    "No articles found in this category.\n" +
                    "Try again later or check /browse for other categories.");
                return;
            }
            
            // Build message with articles
            StringBuilder msg = new StringBuilder();
            msg.append(emoji).append(" *").append(upperCategory).append(" News*\n");
            msg.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
            msg.append("📄 Page ").append(page + 1).append(" of ").append(totalPages);
            msg.append(" (").append(totalArticles).append(" articles)\n\n");
            
            int articleNum = page * ARTICLES_PER_PAGE + 1;
            for (com.notified.notification.model.NewsArticle article : articles) {
                msg.append(articleNum).append(". ");
                msg.append("📰 ").append(article.getTitle() != null ? article.getTitle() : "No title").append("\n");
                
                if (article.getDescription() != null && !article.getDescription().isEmpty()) {
                    String desc = article.getDescription();
                    if (desc.length() > 100) {
                        desc = desc.substring(0, 100) + "...";
                    }
                    msg.append("   ").append(desc).append("\n");
                }
                
                if (article.getLink() != null) {
                    msg.append("   🔗 ").append(article.getLink()).append("\n");
                }
                
                if (article.getPublishedDate() != null) {
                    msg.append("   ⏰ ").append(article.getPublishedDate().format(
                        java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"))).append("\n");
                }
                
                msg.append("\n");
                articleNum++;
            }
            
            // Build pagination keyboard
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            List<Map<String, String>> paginationRow = new ArrayList<>();
            
            // Previous button
            if (page > 0) {
                paginationRow.add(Map.of(
                    "text", "⬅️ Previous",
                    "callback_data", "page_" + category + "_" + (page - 1)
                ));
            }
            
            // Page indicator
            paginationRow.add(Map.of(
                "text", "📄 " + (page + 1) + "/" + totalPages,
                "callback_data", "page_info"
            ));
            
            // Next button
            if (page < totalPages - 1) {
                paginationRow.add(Map.of(
                    "text", "Next ➡️",
                    "callback_data", "page_" + category + "_" + (page + 1)
                ));
            }
            
            keyboard.add(paginationRow);
            
            // Send message with inline keyboard
            sendTelegramMessageWithKeyboard(chatId, msg.toString(), keyboard);
            
        } catch (Exception e) {
            logger.error("Error handling category browse command for {}: {}", category, e.getMessage());
            sendTelegramMessage(chatId, "❌ Error fetching articles. Please try again later.");
        }
    }

    private void handlePaginationCallback(String queryId, String data, String chatId, Map<String, Object> message) {
        try {
            // data format: page_CATEGORY_PAGENUMBER
            if (data.equals("page_info")) {
                answerCallbackQuery(queryId, "Current page");
                return;
            }
            
            String[] parts = data.split("_");
            if (parts.length < 3) {
                answerCallbackQuery(queryId, "Invalid page data");
                return;
            }
            
            String category = parts[1];
            int page = Integer.parseInt(parts[2]);
            
            answerCallbackQuery(queryId, "Loading page " + (page + 1) + "...");
            
            // Delete old message and send new one with updated page
            deleteMessage(chatId, message.get("message_id"));
            handleCategoryBrowseCommand(chatId, category, page);
            
        } catch (Exception e) {
            logger.error("Error handling pagination callback: {}", e.getMessage());
            answerCallbackQuery(queryId, "Error loading page");
        }
    }

    private void sendTelegramMessageWithKeyboard(String chatId, String text, List<List<Map<String, String>>> keyboard) {
        try {
            String token = getToken();
            if (token == null) return;

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            
            Map<String, Object> inlineKeyboard = new HashMap<>();
            inlineKeyboard.put("inline_keyboard", keyboard);
            
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("reply_markup", inlineKeyboard);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            logger.debug("Sent message with keyboard to chatId={} response={}", chatId, response.getStatusCode());

        } catch (Exception e) {
            logger.error("Failed to send Telegram message with keyboard to chatId={}", chatId, e);
        }
    }

    private void deleteMessage(String chatId, Object messageId) {
        try {
            String token = getToken();
            if (token == null) return;

            String url = "https://api.telegram.org/bot" + token + "/deleteMessage";
            
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            restTemplate.postForEntity(url, entity, String.class);
            
        } catch (Exception e) {
            logger.debug("Could not delete message: {}", e.getMessage());
        }
    }
}
