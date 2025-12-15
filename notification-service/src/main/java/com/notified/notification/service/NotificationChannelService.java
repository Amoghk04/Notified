package com.notified.notification.service;

import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.notified.notification.model.NewsArticle;

@Service
public class NotificationChannelService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationChannelService.class);

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;
    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;
    @Value("${twilio.whatsapp.from:}")
    private String twilioWhatsappFrom;

    @Value("${telegram.bot-token:}")
    private String telegramBotToken;

    public NotificationChannelService(JavaMailSender mailSender, RestTemplate restTemplate) {
        // Fail fast if mail sender bean is not present (should be provided by spring-boot-starter-mail)
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    public void sendEmailNotification(UserPreference preference, Notification notification) {
        String email = preference.getEmail();
        if (email == null || email.isEmpty()) {
            logger.warn("Skipping email channel: no email configured for userId={}", preference.getUserId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(notification.getSubject() != null ? notification.getSubject() : "Notification");
            message.setText(notification.getMessage());
            mailSender.send(message);
            logger.info("notificationId={} userId={} channel=EMAIL status=SENT to={}", notification.getId(), preference.getUserId(), email);
        } catch (MailException e) {
            logger.error("notificationId={} userId={} channel=EMAIL status=FAILED to={} mailError={}", notification.getId(), preference.getUserId(), email, e.getMessage());
            throw e; // propagate to allow upstream status handling
        } catch (Exception e) {
            logger.error("notificationId={} userId={} channel=EMAIL status=FAILED to={} unexpectedError", notification.getId(), preference.getUserId(), email, e);
            throw e;
        }
    }

    public void sendWhatsappNotification(UserPreference preference, Notification notification) {
        String phone = preference.getPhoneNumber();
        if (phone == null || phone.isEmpty()) {
            logger.warn("Skipping WhatsApp channel: no phone number for userId={}", preference.getUserId());
            return;
        }
        String accountSid = (twilioAccountSid != null && !twilioAccountSid.isBlank())
            ? twilioAccountSid : System.getenv("TWILIO_ACCOUNT_SID");
        String authToken = (twilioAuthToken != null && !twilioAuthToken.isBlank())
            ? twilioAuthToken : System.getenv("TWILIO_AUTH_TOKEN");
        String fromNumber = (twilioWhatsappFrom != null && !twilioWhatsappFrom.isBlank())
            ? twilioWhatsappFrom : System.getenv("TWILIO_WHATSAPP_FROM"); // e.g. whatsapp:+14155238886
        if (accountSid == null || authToken == null || fromNumber == null || fromNumber.isEmpty()) {
            logger.info("WhatsApp message would be sent to: {} (Twilio creds not configured)", phone);
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            String to = phone.startsWith("whatsapp:") ? phone : "whatsapp:" + phone;
            Message msg = Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), notification.getMessage()).create();
            logger.info("notificationId={} userId={} channel=WHATSAPP status=SENT to={} sid={}", notification.getId(), preference.getUserId(), to, msg.getSid());
        } catch (Exception e) {
            logger.error("notificationId={} userId={} channel=WHATSAPP status=FAILED to={} error={}", notification.getId(), preference.getUserId(), phone, e.getMessage());
            throw e;
        }
    }

    public void sendAppNotification(UserPreference preference, Notification notification) {
        // Push notification integration would go here (e.g., Firebase Cloud Messaging)
        logger.info("App notification would be sent to user: {} with message: {}", 
            preference.getUserId(), notification.getMessage());
    }

    public void sendTelegramNotification(UserPreference preference, Notification notification) {
        String token = (telegramBotToken != null && !telegramBotToken.isBlank())
                ? telegramBotToken
                : System.getenv("TELEGRAM_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            logger.info("Telegram message would be sent (bot token not configured) userId={} chatId={}",
                    preference.getUserId(), preference.getTelegramChatId());
            return;
        }
        String chatId = preference.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) {
            logger.warn("Skipping Telegram channel: no telegramChatId configured for userId={}", preference.getUserId());
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            
            // Build inline keyboard with reaction buttons
            List<Map<String, String>> row = new ArrayList<>();
            row.add(Map.of("text", "👍 Like", "callback_data", "reaction_like_" + notification.getId()));
            row.add(Map.of("text", "👎 Dislike", "callback_data", "reaction_dislike_" + notification.getId()));
            
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(row);
            
            Map<String, Object> inlineKeyboard = new HashMap<>();
            inlineKeyboard.put("inline_keyboard", keyboard);
            
            // Build request body
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", notification.getMessage());
            body.put("reply_markup", inlineKeyboard);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Extract message_id from response
                Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
                if (result != null && result.get("message_id") != null) {
                    Long messageId = ((Number) result.get("message_id")).longValue();
                    notification.setTelegramMessageId(messageId);
                    logger.debug("Captured Telegram message_id={} for notification={}", messageId, notification.getId());
                }
                logger.info("notificationId={} userId={} channel=TELEGRAM status=SENT chatId={}",
                        notification.getId(), preference.getUserId(), chatId);
            } else {
                logger.error("notificationId={} userId={} channel=TELEGRAM status=FAILED chatId={} httpStatus={} body={}",
                        notification.getId(), preference.getUserId(), chatId, response.getStatusCode(), response.getBody());
                throw new RuntimeException("Telegram API returned non-200 status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("notificationId={} userId={} channel=TELEGRAM status=FAILED chatId={} error={}",
                    notification.getId(), preference.getUserId(), chatId, e.getMessage());
            throw e;
        }
    }

    /**
     * Send a consolidated email with multiple articles in a single email
     */
    public void sendConsolidatedEmailNotification(UserPreference preference, List<NewsArticle> articles, String batchId) {
        String email = preference.getEmail();
        if (email == null || email.isEmpty()) {
            logger.warn("Skipping consolidated email: no email configured for userId={}", preference.getUserId());
            return;
        }
        if (articles == null || articles.isEmpty()) {
            logger.warn("Skipping consolidated email: no articles for userId={}", preference.getUserId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("📰 Your News Digest - " + articles.size() + " New Articles");
            
            StringBuilder body = new StringBuilder();
            body.append("Hello ").append(preference.getUserId()).append("!\n\n");
            body.append("Here are your latest news updates:\n");
            body.append("═══════════════════════════════════════\n\n");
            
            int articleNum = 1;
            for (NewsArticle article : articles) {
                body.append("📌 ").append(articleNum++).append(". ").append(article.getCategory().toUpperCase()).append("\n");
                body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                body.append("📰 ").append(article.getTitle()).append("\n\n");
                if (article.getDescription() != null && !article.getDescription().isEmpty()) {
                    body.append(article.getDescription()).append("\n\n");
                }
                body.append("🔗 Read more: ").append(article.getLink()).append("\n");
                body.append("📅 Source: ").append(article.getSource()).append("\n\n");
            }
            
            body.append("═══════════════════════════════════════\n");
            body.append("\nThank you for using Notified!\n");
            body.append("Manage your preferences via Telegram: /updatepref\n");
            
            message.setText(body.toString());
            mailSender.send(message);
            logger.info("batchId={} userId={} channel=EMAIL status=SENT articles={} to={}", 
                batchId, preference.getUserId(), articles.size(), email);
        } catch (MailException e) {
            logger.error("batchId={} userId={} channel=EMAIL status=FAILED to={} mailError={}", 
                batchId, preference.getUserId(), email, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("batchId={} userId={} channel=EMAIL status=FAILED to={} unexpectedError", 
                batchId, preference.getUserId(), email, e);
            throw e;
        }
    }

    /**
     * Send a custom admin broadcast message via email
     */
    public void sendAdminBroadcastEmail(UserPreference preference, String subject, String messageText) {
        String email = preference.getEmail();
        if (email == null || email.isEmpty()) {
            logger.warn("Skipping admin broadcast email: no email configured for userId={}", preference.getUserId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject != null ? subject : "📢 Admin Broadcast");
            message.setText(messageText);
            mailSender.send(message);
            logger.info("userId={} channel=EMAIL status=SENT type=ADMIN_BROADCAST to={}", preference.getUserId(), email);
        } catch (Exception e) {
            logger.error("userId={} channel=EMAIL status=FAILED type=ADMIN_BROADCAST to={} error={}", 
                preference.getUserId(), email, e.getMessage());
            throw e;
        }
    }

    /**
     * Send a custom admin broadcast message via Telegram
     */
    public void sendAdminBroadcastTelegram(UserPreference preference, String messageText) {
        String token = (telegramBotToken != null && !telegramBotToken.isBlank())
                ? telegramBotToken
                : System.getenv("TELEGRAM_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            logger.warn("Skipping admin broadcast Telegram: no bot token configured");
            return;
        }
        String chatId = preference.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) {
            logger.warn("Skipping admin broadcast Telegram: no telegramChatId for userId={}", preference.getUserId());
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📢 ADMIN BROADCAST\n\n" + messageText);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("userId={} channel=TELEGRAM status=SENT type=ADMIN_BROADCAST chatId={}", 
                    preference.getUserId(), chatId);
            } else {
                throw new RuntimeException("Telegram API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("userId={} channel=TELEGRAM status=FAILED type=ADMIN_BROADCAST chatId={} error={}", 
                preference.getUserId(), chatId, e.getMessage());
            throw e;
        }
    }
}
