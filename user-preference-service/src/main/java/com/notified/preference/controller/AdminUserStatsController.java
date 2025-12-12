package com.notified.preference.controller;

import com.notified.preference.model.UserPreference;
import com.notified.preference.repository.UserPreferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/stats")
@CrossOrigin(origins = "*")
public class AdminUserStatsController {

    private final UserPreferenceRepository userPreferenceRepository;

    public AdminUserStatsController(UserPreferenceRepository userPreferenceRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
    }

    /**
     * Get user statistics
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        List<UserPreference> allUsers = userPreferenceRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        
        // Total users
        stats.put("totalUsers", allUsers.size());
        
        // Users with Telegram
        long telegramUsers = allUsers.stream()
            .filter(u -> u.getTelegramChatId() != null && !u.getTelegramChatId().isEmpty())
            .count();
        stats.put("telegramUsers", telegramUsers);
        
        // Users with email
        long emailUsers = allUsers.stream()
            .filter(u -> u.getEmail() != null && !u.getEmail().isEmpty())
            .count();
        stats.put("emailUsers", emailUsers);
        
        // Category preferences distribution
        Map<String, Long> categoryDistribution = new HashMap<>();
        for (UserPreference user : allUsers) {
            if (user.getPreferences() != null) {
                for (String category : user.getPreferences()) {
                    categoryDistribution.merge(category, 1L, Long::sum);
                }
            }
        }
        stats.put("categoryDistribution", categoryDistribution);
        
        // Notification frequency distribution
        Map<String, Long> frequencyDistribution = new HashMap<>();
        for (UserPreference user : allUsers) {
            int interval = user.getNotificationIntervalMinutes();
            String label = getFrequencyLabel(interval);
            frequencyDistribution.merge(label, 1L, Long::sum);
        }
        stats.put("frequencyDistribution", frequencyDistribution);
        
        // Users registered today (based on last notification sent being null = never received)
        long newUsers = allUsers.stream()
            .filter(u -> u.getLastNotificationSent() == null)
            .count();
        stats.put("neverNotifiedUsers", newUsers);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Get all users list for admin
     */
    @GetMapping("/users/list")
    public ResponseEntity<List<Map<String, Object>>> getUsersList() {
        List<UserPreference> allUsers = userPreferenceRepository.findAll();
        
        List<Map<String, Object>> userList = allUsers.stream()
            .map(u -> {
                Map<String, Object> user = new HashMap<>();
                user.put("userId", u.getUserId());
                user.put("email", u.getEmail());
                user.put("hasTelegram", u.getTelegramChatId() != null && !u.getTelegramChatId().isEmpty());
                user.put("telegramUsername", u.getTelegramUsername());
                user.put("categories", u.getPreferences() != null ? u.getPreferences() : Collections.emptyList());
                user.put("frequencyMinutes", u.getNotificationIntervalMinutes());
                user.put("frequencyLabel", getFrequencyLabel(u.getNotificationIntervalMinutes()));
                user.put("lastNotificationSent", u.getLastNotificationSent() != null ? 
                    u.getLastNotificationSent().toString() : null);
                return user;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(userList);
    }

    private String getFrequencyLabel(int minutes) {
        if (minutes <= 1) return "Every Minute";
        if (minutes <= 60) return "Hourly";
        if (minutes <= 720) return "12 Hours";
        if (minutes <= 1440) return "Daily";
        if (minutes <= 2880) return "48 Hours";
        return "Weekly";
    }
}
