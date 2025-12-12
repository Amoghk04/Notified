package com.notified.notification.service;

import com.notified.notification.client.ScraperClient;
import com.notified.notification.model.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for retrieving news articles from the scraper-service.
 * This replaces direct access to NewsScraperService which has been moved to scraper-service.
 */
@Service
public class NewsArticleService {

    private static final Logger logger = LoggerFactory.getLogger(NewsArticleService.class);

    private final ScraperClient scraperClient;

    public NewsArticleService(ScraperClient scraperClient) {
        this.scraperClient = scraperClient;
    }

    /**
     * Get available categories from scraper-service
     */
    public Set<String> getAvailableCategories() {
        try {
            return scraperClient.getCategories();
        } catch (Exception e) {
            logger.error("Error fetching categories from scraper-service: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Get articles by category with a limit
     * @param category The category name
     * @param limit Maximum number of articles to return
     * @return List of articles
     */
    public List<NewsArticle> getArticlesByCategory(String category, int limit) {
        try {
            return scraperClient.getArticlesByCategory(category.toUpperCase(), limit);
        } catch (Exception e) {
            logger.error("Error fetching articles for category {} from scraper-service: {}", category, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get paginated articles for a category (for browsing)
     * @param category The category name
     * @param page Page number (0-indexed)
     * @param pageSize Number of articles per page
     * @return List of articles for the requested page
     */
    @SuppressWarnings("unchecked")
    public List<NewsArticle> getArticlesByCategoryPaginated(String category, int page, int pageSize) {
        try {
            Map<String, Object> response = scraperClient.getArticlesPaginated(category.toUpperCase(), page, pageSize);
            Object articlesObj = response.get("articles");
            if (articlesObj instanceof List) {
                return convertToNewsArticles((List<Map<String, Object>>) articlesObj);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error fetching paginated articles for category {} from scraper-service: {}", category, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Count total articles in a category
     * @param category The category name
     * @return Total number of articles
     */
    public long countArticlesByCategory(String category) {
        try {
            Map<String, Object> response = scraperClient.countArticles(category.toUpperCase());
            Object countObj = response.get("count");
            if (countObj instanceof Number) {
                return ((Number) countObj).longValue();
            }
            return 0L;
        } catch (Exception e) {
            logger.error("Error counting articles for category {} from scraper-service: {}", category, e.getMessage());
            return 0L;
        }
    }

    /**
     * Trigger scraping for all categories (manual trigger)
     */
    public void triggerScraping() {
        try {
            scraperClient.triggerScraping();
            logger.info("Triggered scraping for all categories");
        } catch (Exception e) {
            logger.error("Error triggering scraping: {}", e.getMessage());
        }
    }

    /**
     * Trigger scraping for a specific category
     */
    public void scrapeCategoryNews(String category) {
        try {
            scraperClient.scrapeCategoryNews(category.toUpperCase());
            logger.info("Triggered scraping for category: {}", category);
        } catch (Exception e) {
            logger.error("Error triggering scraping for category {}: {}", category, e.getMessage());
        }
    }

    /**
     * Helper method to convert List of Maps to List of NewsArticle
     */
    private List<NewsArticle> convertToNewsArticles(List<Map<String, Object>> articlesData) {
        List<NewsArticle> articles = new ArrayList<>();
        for (Map<String, Object> data : articlesData) {
            NewsArticle article = new NewsArticle();
            article.setId((String) data.get("id"));
            article.setCategory((String) data.get("category"));
            article.setTitle((String) data.get("title"));
            article.setDescription((String) data.get("description"));
            article.setLink((String) data.get("link"));
            article.setContentHash((String) data.get("contentHash"));
            article.setSource((String) data.get("source"));
            
            // Handle date conversion - may come as string or array from JSON
            Object publishedDate = data.get("publishedDate");
            if (publishedDate != null) {
                article.setPublishedDate(parseLocalDateTime(publishedDate));
            }
            
            Object scrapedAt = data.get("scrapedAt");
            if (scrapedAt != null) {
                article.setScrapedAt(parseLocalDateTime(scrapedAt));
            }
            
            articles.add(article);
        }
        return articles;
    }

    /**
     * Parse LocalDateTime from various formats that may come from JSON
     */
    @SuppressWarnings("unchecked")
    private java.time.LocalDateTime parseLocalDateTime(Object dateObj) {
        try {
            if (dateObj instanceof String) {
                return java.time.LocalDateTime.parse((String) dateObj);
            } else if (dateObj instanceof List) {
                // Jackson sometimes deserializes LocalDateTime as an array [year, month, day, hour, minute, second, nano]
                List<Integer> parts = (List<Integer>) dateObj;
                if (parts.size() >= 5) {
                    int year = parts.get(0);
                    int month = parts.get(1);
                    int day = parts.get(2);
                    int hour = parts.get(3);
                    int minute = parts.get(4);
                    int second = parts.size() > 5 ? parts.get(5) : 0;
                    int nano = parts.size() > 6 ? parts.get(6) : 0;
                    return java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse date: {}", dateObj);
        }
        return null;
    }
}
