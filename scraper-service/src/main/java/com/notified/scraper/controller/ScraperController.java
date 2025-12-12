package com.notified.scraper.controller;

import com.notified.scraper.model.NewsArticle;
import com.notified.scraper.service.NewsScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/scraper")
public class ScraperController {

    private final NewsScraperService newsScraperService;

    public ScraperController(NewsScraperService newsScraperService) {
        this.newsScraperService = newsScraperService;
    }

    /**
     * Get available categories
     */
    @GetMapping("/categories")
    public ResponseEntity<Set<String>> getCategories() {
        return ResponseEntity.ok(newsScraperService.getAvailableCategories());
    }

    /**
     * Trigger scraping for all categories (manual trigger)
     */
    @PostMapping("/scrape")
    public ResponseEntity<Map<String, String>> triggerScraping() {
        newsScraperService.scrapeAllCategories();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for all categories");
        return ResponseEntity.ok(response);
    }

    /**
     * Trigger scraping for a specific category
     */
    @PostMapping("/scrape/{category}")
    public ResponseEntity<Map<String, String>> scrapeCategoryNews(@PathVariable String category) {
        newsScraperService.scrapeCategoryNews(category.toUpperCase());
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for category: " + category);
        return ResponseEntity.ok(response);
    }

    /**
     * Get articles by category with optional limit
     */
    @GetMapping("/articles/{category}")
    public ResponseEntity<List<NewsArticle>> getArticlesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "10") int limit) {
        List<NewsArticle> articles = newsScraperService.getArticlesByCategory(category.toUpperCase(), limit);
        return ResponseEntity.ok(articles);
    }

    /**
     * Get paginated articles by category
     */
    @GetMapping("/articles/{category}/paginated")
    public ResponseEntity<Map<String, Object>> getArticlesPaginated(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int pageSize) {
        
        List<NewsArticle> articles = newsScraperService.getArticlesByCategoryPaginated(
                category.toUpperCase(), page, pageSize);
        long totalCount = newsScraperService.countArticlesByCategory(category.toUpperCase());
        
        Map<String, Object> response = new HashMap<>();
        response.put("articles", articles);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalCount", totalCount);
        response.put("totalPages", (int) Math.ceil((double) totalCount / pageSize));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Count articles in a category
     */
    @GetMapping("/articles/{category}/count")
    public ResponseEntity<Map<String, Object>> countArticles(@PathVariable String category) {
        long count = newsScraperService.countArticlesByCategory(category.toUpperCase());
        Map<String, Object> response = new HashMap<>();
        response.put("category", category.toUpperCase());
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Trigger cleanup of old articles (manual trigger)
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, String>> triggerCleanup() {
        newsScraperService.cleanupOldArticles();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Old articles cleanup completed");
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "scraper-service");
        return ResponseEntity.ok(response);
    }
}
