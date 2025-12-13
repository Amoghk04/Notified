package com.notified.notification.controller;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.UserPreference;
import com.notified.notification.service.NotificationChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/broadcast")
public class AdminBroadcastController {

    private static final Logger logger = LoggerFactory.getLogger(AdminBroadcastController.class);

    private final UserPreferenceClient preferenceClient;
    private final NotificationChannelService channelService;

    public AdminBroadcastController(UserPreferenceClient preferenceClient, 
                                    NotificationChannelService channelService) {
        this.preferenceClient = preferenceClient;
        this.channelService = channelService;
    }

    /**
     * Broadcast a message to all users
     */
    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> broadcastToAll(@RequestBody BroadcastRequest request) {
        logger.info("Admin broadcast to ALL users - subject: {}", request.getSubject());
        
        List<UserPreference> allUsers = preferenceClient.getAllPreferences();
        return sendBroadcast(allUsers, request);
    }

    /**
     * Broadcast a message to selected users
     */
    @PostMapping("/selected")
    public ResponseEntity<Map<String, Object>> broadcastToSelected(@RequestBody SelectedBroadcastRequest request) {
        logger.info("Admin broadcast to {} selected users - subject: {}", 
            request.getUserIds().size(), request.getSubject());
        
        List<UserPreference> allUsers = preferenceClient.getAllPreferences();
        
        // Filter to only selected users
        List<UserPreference> selectedUsers = allUsers.stream()
            .filter(u -> request.getUserIds().contains(u.getUserId()))
            .collect(Collectors.toList());
        
        return sendBroadcast(selectedUsers, request);
    }

    private ResponseEntity<Map<String, Object>> sendBroadcast(List<UserPreference> users, BroadcastRequest request) {
        int emailSuccess = 0;
        int emailFailed = 0;
        int telegramSuccess = 0;
        int telegramFailed = 0;
        int whatsappSuccess = 0;
        int whatsappFailed = 0;

        for (UserPreference user : users) {
            // Send via Email if enabled
            if (user.isEmailEnabled() && user.getEmail() != null) {
                try {
                    channelService.sendAdminBroadcastEmail(user, request.getSubject(), request.getMessage());
                    emailSuccess++;
                } catch (Exception e) {
                    logger.warn("Failed to send broadcast email to {}: {}", user.getUserId(), e.getMessage());
                    emailFailed++;
                }
            }

            // Send via Telegram if enabled
            if (user.isTelegramEnabled() && user.getTelegramChatId() != null) {
                try {
                    channelService.sendAdminBroadcastTelegram(user, request.getMessage());
                    telegramSuccess++;
                } catch (Exception e) {
                    logger.warn("Failed to send broadcast Telegram to {}: {}", user.getUserId(), e.getMessage());
                    telegramFailed++;
                }
            }

            // Send via WhatsApp if enabled (placeholder - uses same notification method)
            if (user.isWhatsappEnabled() && user.getPhoneNumber() != null) {
                // WhatsApp broadcast would go here
                // For now, just log
                logger.info("WhatsApp broadcast to {} would be sent", user.getUserId());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", users.size());
        result.put("email", Map.of("success", emailSuccess, "failed", emailFailed));
        result.put("telegram", Map.of("success", telegramSuccess, "failed", telegramFailed));
        result.put("whatsapp", Map.of("success", whatsappSuccess, "failed", whatsappFailed));
        result.put("message", "Broadcast completed");

        logger.info("Broadcast completed: {} users, email={}/{}, telegram={}/{}", 
            users.size(), emailSuccess, emailFailed, telegramSuccess, telegramFailed);

        return ResponseEntity.ok(result);
    }

    // Request DTOs
    public static class BroadcastRequest {
        private String subject;
        private String message;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class SelectedBroadcastRequest extends BroadcastRequest {
        private List<String> userIds;

        public List<String> getUserIds() { return userIds; }
        public void setUserIds(List<String> userIds) { this.userIds = userIds; }
    }
}
