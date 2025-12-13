package com.notified.notification.controller;

import com.notified.notification.service.RecommenderService;
import com.notified.notification.service.RecommenderService.ScoredArticle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for the News Recommender System.
 * Provides endpoints for:
 * - Getting personalized recommendations
 * - Viewing user preference profiles
 * - Recording reactions (alternative to Telegram callback)
 */
@RestController
@RequestMapping("/recommendations")
public class RecommenderController {

    private final RecommenderService recommenderService;

    public RecommenderController(RecommenderService recommenderService) {
        this.recommenderService = recommenderService;
    }

    /**
     * Get personalized article recommendations for a user
     * 
     * @param userId The user ID
     * @param categories Comma-separated list of categories to include
     * @param limit Maximum number of recommendations (default: 5)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @PathVariable String userId,
            @RequestParam(required = false) String categories,
            @RequestParam(defaultValue = "5") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        // Parse categories
        List<String> categoryList;
        if (categories != null && !categories.isEmpty()) {
            categoryList = List.of(categories.split(","));
        } else {
            // Default to all main categories
            categoryList = List.of("SPORTS", "NEWS", "TECHNOLOGY", "ENTERTAINMENT", 
                    "FINANCE", "HEALTH", "TRAVEL", "EDUCATION");
        }

        List<ScoredArticle> recommendations = recommenderService.getRecommendations(
                userId, categoryList, limit);

        // Convert to response format
        List<Map<String, Object>> articlesResponse = recommendations.stream()
                .map(scored -> {
                    Map<String, Object> articleMap = new HashMap<>();
                    articleMap.put("id", scored.getArticle().getId());
                    articleMap.put("title", scored.getArticle().getTitle());
                    articleMap.put("description", scored.getArticle().getDescription());
                    articleMap.put("category", scored.getArticle().getCategory());
                    articleMap.put("source", scored.getArticle().getSource());
                    articleMap.put("link", scored.getArticle().getLink());
                    articleMap.put("publishedDate", scored.getArticle().getPublishedDate());
                    articleMap.put("recommendationScore", Math.round(scored.getScore() * 100) / 100.0);
                    articleMap.put("scoreExplanation", scored.getExplanation());
                    return articleMap;
                })
                .collect(Collectors.toList());

        response.put("userId", userId);
        response.put("count", articlesResponse.size());
        response.put("recommendations", articlesResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * Get user's preference profile summary
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable String userId) {
        Map<String, Object> profile = recommenderService.getUserPreferenceSummary(userId);
        profile.put("userId", userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * Record a reaction to an article (alternative to Telegram callback)
     */
    @PostMapping("/{userId}/react")
    public ResponseEntity<Map<String, Object>> recordReaction(
            @PathVariable String userId,
            @RequestBody ReactionRequest request) {
        
        recommenderService.recordReaction(
                userId,
                request.getReaction(),
                request.getCategory(),
                request.getSource(),
                request.getTitle()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Reaction recorded successfully");
        response.put("userId", userId);
        response.put("reaction", request.getReaction());

        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger decay on all user preferences (admin endpoint)
     */
    @PostMapping("/admin/apply-decay")
    public ResponseEntity<Map<String, Object>> applyDecay() {
        recommenderService.applyDecayToAllUsers();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Decay applied to all user preferences");
        
        return ResponseEntity.ok(response);
    }

    // Request DTO for reactions
    public static class ReactionRequest {
        private String reaction; // "like" or "dislike"
        private String category;
        private String source;
        private String title;

        public String getReaction() {
            return reaction;
        }

        public void setReaction(String reaction) {
            this.reaction = reaction;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
