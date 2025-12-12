package com.notified.notification.controller;

import com.notified.notification.model.Notification;
import com.notified.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/stats")
@CrossOrigin(origins = "*")
public class AdminStatsController {

    private final NotificationRepository notificationRepository;

    public AdminStatsController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Get overall notification statistics
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotificationStats() {
        List<Notification> allNotifications = notificationRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        
        // Total counts
        stats.put("totalNotifications", allNotifications.size());
        
        // Count by status
        Map<String, Long> byStatus = allNotifications.stream()
            .filter(n -> n.getStatus() != null)
            .collect(Collectors.groupingBy(
                n -> n.getStatus().name(),
                Collectors.counting()
            ));
        stats.put("byStatus", byStatus);
        
        // Count by channel
        Map<String, Long> byChannel = new HashMap<>();
        for (Notification n : allNotifications) {
            if (n.getChannels() != null) {
                for (Notification.NotificationChannel channel : n.getChannels()) {
                    byChannel.merge(channel.name(), 1L, Long::sum);
                }
            }
        }
        stats.put("byChannel", byChannel);
        
        // Reactions stats
        long likes = allNotifications.stream()
            .filter(n -> "like".equals(n.getUserReaction()))
            .count();
        long dislikes = allNotifications.stream()
            .filter(n -> "dislike".equals(n.getUserReaction()))
            .count();
        stats.put("reactions", Map.of("likes", likes, "dislikes", dislikes));
        
        // Last 24 hours
        LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
        long sentLast24Hours = allNotifications.stream()
            .filter(n -> n.getSentAt() != null && n.getSentAt().isAfter(last24Hours))
            .count();
        stats.put("sentLast24Hours", sentLast24Hours);
        
        // Last 7 days
        LocalDateTime last7Days = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        long sentLast7Days = allNotifications.stream()
            .filter(n -> n.getSentAt() != null && n.getSentAt().isAfter(last7Days))
            .count();
        stats.put("sentLast7Days", sentLast7Days);
        
        // Daily breakdown for last 7 days
        Map<String, Long> dailyBreakdown = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now().minus(i, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            LocalDateTime dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
            
            long count = allNotifications.stream()
                .filter(n -> n.getSentAt() != null && 
                        n.getSentAt().isAfter(dayStart) && 
                        n.getSentAt().isBefore(dayEnd))
                .count();
            
            String dayLabel = dayStart.toLocalDate().toString();
            dailyBreakdown.put(dayLabel, count);
        }
        stats.put("dailyBreakdown", dailyBreakdown);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Get recent notifications (for activity feed)
     */
    @GetMapping("/notifications/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentNotifications(
            @RequestParam(defaultValue = "20") int limit) {
        
        List<Notification> allNotifications = notificationRepository.findAll();
        
        // Sort by sentAt descending and limit
        List<Map<String, Object>> recent = allNotifications.stream()
            .filter(n -> n.getSentAt() != null)
            .sorted((a, b) -> b.getSentAt().compareTo(a.getSentAt()))
            .limit(limit)
            .map(n -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", n.getId());
                item.put("userId", n.getUserId());
                item.put("subject", n.getSubject());
                item.put("status", n.getStatus() != null ? n.getStatus().name() : "UNKNOWN");
                item.put("sentAt", n.getSentAt() != null ? n.getSentAt().toString() : null);
                item.put("channels", n.getChannels() != null ? 
                    n.getChannels().stream().map(Enum::name).collect(Collectors.toList()) : 
                    Collections.emptyList());
                item.put("reaction", n.getUserReaction());
                return item;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(recent);
    }

    /**
     * Get unique users count
     */
    @GetMapping("/users/count")
    public ResponseEntity<Map<String, Object>> getUniqueUsersCount() {
        List<Notification> allNotifications = notificationRepository.findAll();
        
        long uniqueUsers = allNotifications.stream()
            .map(Notification::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        
        return ResponseEntity.ok(Map.of("uniqueUsers", uniqueUsers));
    }
}
