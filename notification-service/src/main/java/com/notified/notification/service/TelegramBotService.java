package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;

@Service
public class TelegramBotService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot-token:}")
    private String telegramBotToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserPreferenceClient preferenceClient;

    public TelegramBotService(UserPreferenceClient preferenceClient) {
        this.preferenceClient = preferenceClient;
    }

    public void processUpdate(Map<String, Object> update) {
        try {
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

            // Handle /start command
            if (text != null && text.startsWith("/start")) {
                handleStartCommand(chatId, firstName, username);
            } else if (text != null) {
                // Echo back for now
                sendTelegramMessage(chatId, "I received: " + text + "\nUse /start to register your chat ID.");
            }

        } catch (Exception e) {
            logger.error("Error processing Telegram update", e);
        }
    }

    private void handleStartCommand(String chatId, String firstName, String username) {
        try {
            // Send welcome message
            String welcomeMsg = String.format(
                "👋 Welcome to Notified, %s!\n\n" +
                "Your Telegram Chat ID is: %s\n\n" +
                "To receive notifications:\n" +
                "1. Go to http://localhost:3000\n" +
                "2. Enter your User ID (e.g., your name or email)\n" +
                "3. Paste this Chat ID: %s\n" +
                "4. Enable the Telegram channel\n" +
                "5. Save your preferences\n\n" +
                "Then you'll receive notifications here!",
                firstName != null ? firstName : "friend",
                chatId,
                chatId
            );
            
            sendTelegramMessage(chatId, welcomeMsg);
            
            logger.info("Handled /start for chatId={} firstName={} username={}", 
                chatId, firstName, username);

        } catch (Exception e) {
            logger.error("Error handling /start command for chatId={}", chatId, e);
            sendTelegramMessage(chatId, "Sorry, something went wrong. Please try again later.");
        }
    }

    private void sendTelegramMessage(String chatId, String text) {
        try {
            String token = (telegramBotToken != null && !telegramBotToken.isBlank())
                    ? telegramBotToken
                    : System.getenv("TELEGRAM_BOT_TOKEN");

            if (token == null || token.isBlank()) {
                logger.warn("Telegram bot token not configured, cannot send message");
                return;
            }

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
