package com.notified.notification.client;

import com.notified.notification.model.NewsArticle;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

@FeignClient(name = "scraper-service")
public interface ScraperClient {

    /**
     * Get available categories
     */
    @GetMapping("/api/scraper/categories")
    Set<String> getCategories();

    /**
     * Trigger scraping for all categories
     */
    @PostMapping("/api/scraper/scrape")
    Map<String, String> triggerScraping();

    /**
     * Trigger scraping for a specific category
     */
    @PostMapping("/api/scraper/scrape/{category}")
    Map<String, String> scrapeCategoryNews(@PathVariable("category") String category);

    /**
     * Get articles by category with optional limit
     */
    @GetMapping("/api/scraper/articles/{category}")
    List<NewsArticle> getArticlesByCategory(
            @PathVariable("category") String category,
            @RequestParam(value = "limit", defaultValue = "10") int limit);

    /**
     * Get paginated articles by category
     */
    @GetMapping("/api/scraper/articles/{category}/paginated")
    Map<String, Object> getArticlesPaginated(
            @PathVariable("category") String category,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "5") int pageSize);

    /**
     * Count articles in a category
     */
    @GetMapping("/api/scraper/articles/{category}/count")
    Map<String, Object> countArticles(@PathVariable("category") String category);

    /**
     * Trigger cleanup of old articles
     */
    @PostMapping("/api/scraper/cleanup")
    Map<String, String> triggerCleanup();

    /**
     * Health check
     */
    @GetMapping("/api/scraper/health")
    Map<String, String> health();
}
