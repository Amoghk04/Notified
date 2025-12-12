package com.notified.notification.controller;

import com.notified.notification.model.NewsArticle;
import com.notified.notification.service.NewsArticleService;
import com.notified.notification.service.NewsNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/news")
public class NewsController {

    private final NewsArticleService newsArticleService;
    private final NewsNotificationService newsNotificationService;

    public NewsController(NewsArticleService newsArticleService,
                         NewsNotificationService newsNotificationService) {
        this.newsArticleService = newsArticleService;
        this.newsNotificationService = newsNotificationService;
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, String>> triggerScraping() {
        newsArticleService.triggerScraping();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for all categories");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scrape/{category}")
    public ResponseEntity<Map<String, String>> scrapeCategoryNews(@PathVariable String category) {
        newsArticleService.scrapeCategoryNews(category.toUpperCase());
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "News scraping triggered for category: " + category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<NewsArticle>> getNewsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "10") int limit) {
        List<NewsArticle> articles = newsArticleService.getArticlesByCategory(category.toUpperCase(), limit);
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
