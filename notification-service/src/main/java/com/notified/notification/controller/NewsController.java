package com.notified.notification.controller;

import com.notified.notification.model.NewsArticle;
import com.notified.notification.service.NewsNotificationService;
import com.notified.notification.service.NewsScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/news")
public class NewsController {

    private final NewsScraperService newsScraperService;
    private final NewsNotificationService newsNotificationService;

    public NewsController(NewsScraperService newsScraperService,
                         NewsNotificationService newsNotificationService) {
        this.newsScraperService = newsScraperService;
        this.newsNotificationService = newsNotificationService;
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, String>> triggerScraping() {
        newsScraperService.scrapeAllCategories();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for all categories");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scrape/{category}")
    public ResponseEntity<Map<String, String>> scrapeCategoryNews(@PathVariable String category) {
        newsScraperService.scrapeCategoryNews(category.toUpperCase());
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for category: " + category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<NewsArticle>> getNewsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "10") int limit) {
        List<NewsArticle> articles = newsScraperService.getArticlesByCategory(category.toUpperCase(), limit);
        return ResponseEntity.ok(articles);
    }

    @PostMapping("/send/{userId}")
    public ResponseEntity<Map<String, String>> sendNewsToUser(@PathVariable String userId) {
        newsNotificationService.sendNewsToUserNow(userId);
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News notifications sent to user: " + userId);
        return ResponseEntity.ok(response);
    }
}
