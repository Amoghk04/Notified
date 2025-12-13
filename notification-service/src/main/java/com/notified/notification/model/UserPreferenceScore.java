package com.notified.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores learned user preferences based on their reactions (likes/dislikes).
 * This model tracks preference scores for categories, sources, and keywords.
 * 
 * Scoring system:
 * - Like: +1 to relevant scores
 * - Dislike: -1 to relevant scores
 * 
 * These scores are used by the RecommenderService to personalize news delivery.
 */
@Document(collection = "user_preference_scores")
public class UserPreferenceScore {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    // Category preference scores (e.g., SPORTS: +5, POLITICS: -2)
    private Map<String, Double> categoryScores = new HashMap<>();

    // News source preference scores (e.g., BBC: +3, CNN: -1)
    private Map<String, Double> sourceScores = new HashMap<>();

    // Keyword preference scores extracted from article titles
    // (e.g., "cricket": +4, "election": -2)
    private Map<String, Double> keywordScores = new HashMap<>();

    // Total number of reactions (for normalization)
    private int totalLikes = 0;
    private int totalDislikes = 0;

    // Last time the preferences were updated
    private LocalDateTime lastUpdated;

    // Decay factor for older preferences (0.0 - 1.0)
    // Higher values mean older preferences have more weight
    private double decayFactor = 0.95;

    public UserPreferenceScore() {
        this.lastUpdated = LocalDateTime.now();
    }

    public UserPreferenceScore(String userId) {
        this();
        this.userId = userId;
    }

    // Methods to update scores based on reactions

    /**
     * Update scores when user likes an article
     */
    public void recordLike(String category, String source, String[] keywords) {
        updateScore(category, source, keywords, 1.0);
        totalLikes++;
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Update scores when user dislikes an article
     */
    public void recordDislike(String category, String source, String[] keywords) {
        updateScore(category, source, keywords, -1.0);
        totalDislikes++;
        lastUpdated = LocalDateTime.now();
    }

    private void updateScore(String category, String source, String[] keywords, double delta) {
        // Update category score
        if (category != null && !category.isEmpty()) {
            String normalizedCategory = category.toUpperCase();
            categoryScores.merge(normalizedCategory, delta, Double::sum);
        }

        // Update source score
        if (source != null && !source.isEmpty()) {
            String normalizedSource = normalizeSource(source);
            sourceScores.merge(normalizedSource, delta, Double::sum);
        }

        // Update keyword scores
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isEmpty() && keyword.length() > 2) {
                    String normalizedKeyword = keyword.toLowerCase().trim();
                    keywordScores.merge(normalizedKeyword, delta, Double::sum);
                }
            }
        }
    }

    /**
     * Normalize source name for consistent matching
     */
    private String normalizeSource(String source) {
        if (source == null) return "unknown";
        // Extract main source name (e.g., "BBC Sport" -> "BBC")
        String normalized = source.toLowerCase().trim();
        // Remove common suffixes
        normalized = normalized.replaceAll("\\s*(news|sport|sports|india|world|tech|entertainment)\\s*$", "").trim();
        return normalized.isEmpty() ? source.toLowerCase() : normalized;
    }

    /**
     * Get the preference score for a category
     */
    public double getCategoryScore(String category) {
        if (category == null) return 0.0;
        return categoryScores.getOrDefault(category.toUpperCase(), 0.0);
    }

    /**
     * Get the preference score for a source
     */
    public double getSourceScore(String source) {
        if (source == null) return 0.0;
        return sourceScores.getOrDefault(normalizeSource(source), 0.0);
    }

    /**
     * Get the preference score for a keyword
     */
    public double getKeywordScore(String keyword) {
        if (keyword == null) return 0.0;
        return keywordScores.getOrDefault(keyword.toLowerCase().trim(), 0.0);
    }

    /**
     * Apply decay to all scores (call periodically to reduce impact of old preferences)
     */
    public void applyDecay() {
        categoryScores.replaceAll((k, v) -> v * decayFactor);
        sourceScores.replaceAll((k, v) -> v * decayFactor);
        keywordScores.replaceAll((k, v) -> v * decayFactor);
        
        // Remove scores that are too small
        categoryScores.entrySet().removeIf(e -> Math.abs(e.getValue()) < 0.1);
        sourceScores.entrySet().removeIf(e -> Math.abs(e.getValue()) < 0.1);
        keywordScores.entrySet().removeIf(e -> Math.abs(e.getValue()) < 0.1);
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Double> getCategoryScores() {
        return categoryScores;
    }

    public void setCategoryScores(Map<String, Double> categoryScores) {
        this.categoryScores = categoryScores;
    }

    public Map<String, Double> getSourceScores() {
        return sourceScores;
    }

    public void setSourceScores(Map<String, Double> sourceScores) {
        this.sourceScores = sourceScores;
    }

    public Map<String, Double> getKeywordScores() {
        return keywordScores;
    }

    public void setKeywordScores(Map<String, Double> keywordScores) {
        this.keywordScores = keywordScores;
    }

    public int getTotalLikes() {
        return totalLikes;
    }

    public void setTotalLikes(int totalLikes) {
        this.totalLikes = totalLikes;
    }

    public int getTotalDislikes() {
        return totalDislikes;
    }

    public void setTotalDislikes(int totalDislikes) {
        this.totalDislikes = totalDislikes;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public double getDecayFactor() {
        return decayFactor;
    }

    public void setDecayFactor(double decayFactor) {
        this.decayFactor = decayFactor;
    }

    /**
     * Get top N preferred categories
     */
    public Map<String, Double> getTopCategories(int n) {
        return categoryScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }

    /**
     * Get top N preferred sources
     */
    public Map<String, Double> getTopSources(int n) {
        return sourceScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }

    /**
     * Get top N preferred keywords
     */
    public Map<String, Double> getTopKeywords(int n) {
        return keywordScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }
}
