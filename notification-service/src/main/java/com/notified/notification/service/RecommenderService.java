package com.notified.notification.service;

import com.notified.notification.model.NewsArticle;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreferenceScore;
import com.notified.notification.repository.NotificationRepository;
import com.notified.notification.repository.UserPreferenceScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recommender Service that provides personalized news recommendations
 * based on user reactions (likes/dislikes).
 * 
 * Algorithm:
 * 1. Extract features from articles (category, source, keywords)
 * 2. Score each article based on user preference scores
 * 3. Rank articles by score
 * 4. Filter out already-seen articles
 * 5. Return top N recommendations
 */
@Service
public class RecommenderService {

    private static final Logger logger = LoggerFactory.getLogger(RecommenderService.class);

    private final UserPreferenceScoreRepository preferenceScoreRepository;
    private final NotificationRepository notificationRepository;
    private final NewsArticleService newsArticleService;

    // Weights for different scoring components
    private static final double CATEGORY_WEIGHT = 0.4;
    private static final double SOURCE_WEIGHT = 0.2;
    private static final double KEYWORD_WEIGHT = 0.3;
    private static final double RECENCY_WEIGHT = 0.1;

    // Stop words to exclude from keyword extraction
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
            "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
            "into", "through", "during", "before", "after", "above", "below",
            "between", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "just", "and", "but", "if", "or",
            "because", "until", "while", "this", "that", "these", "those", "what",
            "which", "who", "whom", "its", "it", "he", "she", "they", "them",
            "his", "her", "their", "my", "your", "our", "says", "said", "new",
            "news", "latest", "today", "now", "get", "got", "make", "made"
    );

    // Pattern to match words
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    public RecommenderService(UserPreferenceScoreRepository preferenceScoreRepository,
                              NotificationRepository notificationRepository,
                              NewsArticleService newsArticleService) {
        this.preferenceScoreRepository = preferenceScoreRepository;
        this.notificationRepository = notificationRepository;
        this.newsArticleService = newsArticleService;
    }

    /**
     * Record a user's reaction to an article and update their preference scores
     */
    public void recordReaction(String userId, String reaction, String category, 
                                String source, String title) {
        UserPreferenceScore preferenceScore = preferenceScoreRepository
                .findByUserId(userId)
                .orElse(new UserPreferenceScore(userId));

        String[] keywords = extractKeywords(title);

        if ("like".equalsIgnoreCase(reaction)) {
            preferenceScore.recordLike(category, source, keywords);
            logger.info("Recorded LIKE for user {} - category: {}, source: {}, keywords: {}", 
                    userId, category, source, Arrays.toString(keywords));
        } else if ("dislike".equalsIgnoreCase(reaction)) {
            preferenceScore.recordDislike(category, source, keywords);
            logger.info("Recorded DISLIKE for user {} - category: {}, source: {}, keywords: {}", 
                    userId, category, source, keywords);
        }

        preferenceScoreRepository.save(preferenceScore);
    }

    /**
     * Get personalized article recommendations for a user
     * 
     * @param userId The user to get recommendations for
     * @param categories List of categories the user is subscribed to
     * @param maxArticles Maximum number of articles to return
     * @return List of recommended articles, sorted by relevance score
     */
    public List<ScoredArticle> getRecommendations(String userId, List<String> categories, int maxArticles) {
        // Get user's preference scores
        Optional<UserPreferenceScore> preferenceScoreOpt = preferenceScoreRepository.findByUserId(userId);
        
        // Collect all articles from user's subscribed categories
        List<NewsArticle> allArticles = new ArrayList<>();
        for (String category : categories) {
            try {
                List<NewsArticle> categoryArticles = newsArticleService.getArticlesByCategory(category, 10);
                allArticles.addAll(categoryArticles);
            } catch (Exception e) {
                logger.warn("Error fetching articles for category {}: {}", category, e.getMessage());
            }
        }

        if (allArticles.isEmpty()) {
            logger.info("No articles available for user {} in categories: {}", userId, categories);
            return Collections.emptyList();
        }

        // Filter out already-seen articles
        List<NewsArticle> unseenArticles = allArticles.stream()
                .filter(article -> !notificationRepository.existsByUserIdAndArticleContentHash(
                        userId, article.getContentHash()))
                .collect(Collectors.toList());

        if (unseenArticles.isEmpty()) {
            logger.info("User {} has seen all available articles", userId);
            return Collections.emptyList();
        }

        // Score each article
        List<ScoredArticle> scoredArticles;
        
        if (preferenceScoreOpt.isPresent()) {
            UserPreferenceScore preferenceScore = preferenceScoreOpt.get();
            scoredArticles = unseenArticles.stream()
                    .map(article -> scoreArticle(article, preferenceScore))
                    .sorted(Comparator.comparingDouble(ScoredArticle::getScore).reversed())
                    .limit(maxArticles)
                    .collect(Collectors.toList());
            
            logger.info("Generated {} personalized recommendations for user {}", 
                    scoredArticles.size(), userId);
        } else {
            // No preference data yet - return articles in recency order
            scoredArticles = unseenArticles.stream()
                    .map(article -> new ScoredArticle(article, 0.0, "No preference data yet"))
                    .limit(maxArticles)
                    .collect(Collectors.toList());
            
            logger.info("No preference data for user {} - returning {} recent articles", 
                    userId, scoredArticles.size());
        }

        return scoredArticles;
    }

    /**
     * Score an article based on user preferences
     */
    private ScoredArticle scoreArticle(NewsArticle article, UserPreferenceScore preferenceScore) {
        double categoryScore = preferenceScore.getCategoryScore(article.getCategory());
        double sourceScore = preferenceScore.getSourceScore(article.getSource());
        
        // Calculate keyword score (average of matching keywords)
        String[] articleKeywords = extractKeywords(article.getTitle());
        double keywordScore = 0.0;
        int matchingKeywords = 0;
        for (String keyword : articleKeywords) {
            double kwScore = preferenceScore.getKeywordScore(keyword);
            if (kwScore != 0) {
                keywordScore += kwScore;
                matchingKeywords++;
            }
        }
        if (matchingKeywords > 0) {
            keywordScore /= matchingKeywords;
        }

        // Calculate recency score (0-1, newer = higher)
        double recencyScore = 0.5; // Default
        if (article.getPublishedDate() != null) {
            long hoursOld = java.time.Duration.between(
                    article.getPublishedDate(), 
                    java.time.LocalDateTime.now()
            ).toHours();
            recencyScore = Math.max(0, 1.0 - (hoursOld / 168.0)); // Decay over 7 days
        }

        // Calculate final score
        double finalScore = (CATEGORY_WEIGHT * categoryScore) +
                           (SOURCE_WEIGHT * sourceScore) +
                           (KEYWORD_WEIGHT * keywordScore) +
                           (RECENCY_WEIGHT * recencyScore);

        // Build explanation
        String explanation = String.format(
                "Category(%s): %.2f, Source(%s): %.2f, Keywords: %.2f, Recency: %.2f",
                article.getCategory(), categoryScore,
                article.getSource(), sourceScore,
                keywordScore, recencyScore
        );

        return new ScoredArticle(article, finalScore, explanation);
    }

    /**
     * Extract meaningful keywords from text (title/description)
     */
    public String[] extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        // Split text into words, filter stop words and short words
        return WORD_PATTERN.matcher(text.toLowerCase()).results()
                .map(m -> m.group())
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * Get user's preference profile summary
     */
    public Map<String, Object> getUserPreferenceSummary(String userId) {
        Map<String, Object> summary = new HashMap<>();
        
        Optional<UserPreferenceScore> preferenceScoreOpt = preferenceScoreRepository.findByUserId(userId);
        
        if (preferenceScoreOpt.isEmpty()) {
            summary.put("status", "no_data");
            summary.put("message", "No preference data yet. React to some articles to build your profile!");
            return summary;
        }

        UserPreferenceScore preferenceScore = preferenceScoreOpt.get();
        
        summary.put("status", "active");
        summary.put("totalLikes", preferenceScore.getTotalLikes());
        summary.put("totalDislikes", preferenceScore.getTotalDislikes());
        summary.put("lastUpdated", preferenceScore.getLastUpdated());
        summary.put("topCategories", preferenceScore.getTopCategories(5));
        summary.put("topSources", preferenceScore.getTopSources(5));
        summary.put("topKeywords", preferenceScore.getTopKeywords(10));
        
        // Also include least preferred (negative scores)
        Map<String, Double> leastPreferredCategories = preferenceScore.getCategoryScores().entrySet().stream()
                .filter(e -> e.getValue() < 0)
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        summary.put("leastPreferredCategories", leastPreferredCategories);

        return summary;
    }

    /**
     * Apply decay to old preferences (call periodically, e.g., weekly)
     */
    public void applyDecayToAllUsers() {
        List<UserPreferenceScore> allScores = preferenceScoreRepository.findAll();
        for (UserPreferenceScore score : allScores) {
            score.applyDecay();
            preferenceScoreRepository.save(score);
        }
        logger.info("Applied decay to {} user preference profiles", allScores.size());
    }

    /**
     * Inner class to hold an article with its recommendation score
     */
    public static class ScoredArticle {
        private final NewsArticle article;
        private final double score;
        private final String explanation;

        public ScoredArticle(NewsArticle article, double score, String explanation) {
            this.article = article;
            this.score = score;
            this.explanation = explanation;
        }

        public NewsArticle getArticle() {
            return article;
        }

        public double getScore() {
            return score;
        }

        public String getExplanation() {
            return explanation;
        }

        @Override
        public String toString() {
            return String.format("ScoredArticle{title='%s', score=%.2f, explanation='%s'}",
                    article.getTitle(), score, explanation);
        }
    }
}
