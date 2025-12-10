package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import com.notified.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    
    // Store user states for multi-step flows
    private final Map<String, UserRegistrationState> userStates = new ConcurrentHashMap<>();

    public TelegramBotService(UserPreferenceClient preferenceClient, NotificationRepository notificationRepository) {
        this.preferenceClient = preferenceClient;
        this.notificationRepository = notificationRepository;
    }

    private static class UserRegistrationState {
        String userId;
        String email;
        Set<String> selectedCategories = new HashSet<>();
        boolean waitingForUserId;
        boolean waitingForEmail;
        boolean waitingForCategories;
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
            if (text.startsWith("/start")) {
                handleStartCommand(chatId, firstName, username);
            } else if (text.startsWith("/register")) {
                handleRegisterCommand(chatId);
            } else if (text.startsWith("/history")) {
                handleHistoryCommand(chatId);
            } else if (text.startsWith("/update")) {
                handleUpdatePreferencesCommand(chatId);
            } else if (text.startsWith("/help")) {
                handleHelpCommand(chatId);
            } else {
                sendTelegramMessage(chatId, "Unknown command. Try /help for available commands.");
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
                // Refresh the keyboard
                sendCategorySelection(chatId, state.selectedCategories);
            } else if (data.equals("done_categories")) {
                answerCallbackQuery(queryId, "Preferences saved!");
                finishRegistration(chatId, state);
            }

        } catch (Exception e) {
            logger.error("Error handling callback query", e);
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
            state.waitingForEmail = true;
            sendTelegramMessage(chatId, "Great! Now please enter your email address:");
        } else if (state.waitingForEmail) {
            state.email = text.trim();
            state.waitingForEmail = false;
            state.waitingForCategories = true;
            sendTelegramMessage(chatId, "Perfect! Now select the notification categories you're interested in:");
            sendCategorySelection(chatId, state.selectedCategories);
        }
    }

    private void sendCategorySelection(String chatId, Set<String> selectedCategories) {
        String[] categories = {"SPORTS", "NEWS", "WEATHER", "SHOPPING", "FINANCE", 
                               "ENTERTAINMENT", "HEALTH", "TECHNOLOGY", "TRAVEL", 
                               "SOCIAL", "EDUCATION", "PROMOTIONS"};
        
        Map<String, String> categoryEmojis = Map.ofEntries(
            Map.entry("SPORTS", "⚽"), Map.entry("NEWS", "📰"), Map.entry("WEATHER", "🌤️"),
            Map.entry("SHOPPING", "🛒"), Map.entry("FINANCE", "💰"), Map.entry("ENTERTAINMENT", "🎬"),
            Map.entry("HEALTH", "🏥"), Map.entry("TECHNOLOGY", "💻"), Map.entry("TRAVEL", "✈️"),
            Map.entry("SOCIAL", "👥"), Map.entry("EDUCATION", "📚"), Map.entry("PROMOTIONS", "🎁")
        );

        StringBuilder msg = new StringBuilder("Select your preferences (tap to toggle):\n\n");
        for (String cat : categories) {
            String mark = selectedCategories.contains(cat) ? "✅" : "⬜";
            msg.append(mark).append(" ").append(categoryEmojis.getOrDefault(cat, "📌"))
               .append(" ").append(cat).append("\n");
        }
        msg.append("\nSelected: ").append(selectedCategories.size()).append(" categories");

        sendMessageWithInlineKeyboard(chatId, msg.toString(), categories, categoryEmojis, selectedCategories);
    }

    private void sendMessageWithInlineKeyboard(String chatId, String text, String[] categories, 
                                                Map<String, String> emojis, Set<String> selected) {
        try {
            String token = getToken();
            if (token == null) return;

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            
            // Build inline keyboard
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            for (int i = 0; i < categories.length; i += 2) {
                List<Map<String, String>> row = new ArrayList<>();
                for (int j = i; j < Math.min(i + 2, categories.length); j++) {
                    String cat = categories[j];
                    String mark = selected.contains(cat) ? "✅" : "⬜";
                    Map<String, String> button = new HashMap<>();
                    button.put("text", mark + " " + emojis.getOrDefault(cat, "📌") + " " + cat);
                    button.put("callback_data", "cat_" + cat);
                    row.add(button);
                }
                keyboard.add(row);
            }
            
            // Add done button
            List<Map<String, String>> doneRow = new ArrayList<>();
            Map<String, String> doneButton = new HashMap<>();
            doneButton.put("text", "✅ Done - Save Preferences");
            doneButton.put("callback_data", "done_categories");
            doneRow.add(doneButton);
            keyboard.add(doneRow);

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", keyboard);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("reply_markup", replyMarkup);

            restTemplate.postForEntity(url, body, String.class);

        } catch (Exception e) {
            logger.error("Failed to send message with keyboard", e);
        }
    }

    private void finishRegistration(String chatId, UserRegistrationState state) {
        try {
            // Create or update user preference
            UserPreference pref = new UserPreference();
            pref.setUserId(state.userId);
            pref.setEmail(state.email);
            pref.setTelegramChatId(chatId);
            
            Set<UserPreference.NotificationChannel> channels = new HashSet<>();
            channels.add(UserPreference.NotificationChannel.EMAIL);
            channels.add(UserPreference.NotificationChannel.TELEGRAM);
            pref.setEnabledChannels(channels);

            // Update via API
            String apiUrl = "http://localhost:8081/preferences";
            try {
                // Try to update existing
                restTemplate.put(apiUrl + "/" + state.userId, pref);
            } catch (Exception e) {
                // Create new if doesn't exist
                restTemplate.postForEntity(apiUrl, pref, UserPreference.class);
            }

            String successMsg = String.format(
                "🎉 Registration complete!\n\n" +
                "📋 Your Details:\n" +
                "User ID: %s\n" +
                "Email: %s\n" +
                "Categories: %s\n\n" +
                "You'll now receive notifications here!\n\n" +
                "Commands:\n" +
                "/history - View your notifications\n" +
                "/update - Update preferences\n" +
                "/help - Show help",
                state.userId,
                state.email,
                state.selectedCategories.isEmpty() ? "None" : String.join(", ", state.selectedCategories)
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
                "/history - View your notification history\n" +
                "/update - Update your preferences\n" +
                "/help - Show this help message\n\n" +
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

            // Start update flow
            UserRegistrationState state = new UserRegistrationState();
            state.userId = userPref.getUserId();
            state.email = userPref.getEmail();
            state.waitingForCategories = true;
            userStates.put(chatId, state);

            sendTelegramMessage(chatId, "Let's update your preferences! Select your notification categories:");
            sendCategorySelection(chatId, state.selectedCategories);

        } catch (Exception e) {
            logger.error("Error handling /update for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "❌ Failed to start update. Please try again later.");
        }
    }

    private void handleHelpCommand(String chatId) {
        String helpMsg = 
            "🤖 Notified Bot Help\n\n" +
            "📋 Available Commands:\n\n" +
            "/start - Welcome message\n" +
            "/register - Register and set preferences\n" +
            "/history - View notification history\n" +
            "/update - Update your preferences\n" +
            "/help - Show this help message\n\n" +
            "ℹ️ This bot helps you manage notifications directly through Telegram!";
        
        sendTelegramMessage(chatId, helpMsg);
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
}
