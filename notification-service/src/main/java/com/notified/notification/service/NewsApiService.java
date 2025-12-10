package com.notified.notification.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class NewsApiService {

    private static final Logger logger = LoggerFactory.getLogger(NewsApiService.class);

    @Value("${news.api.token:}")
    private String apiToken;

    @Value("${news.api.base-url:https://api.thenewsapi.com/v1/news/all}")
    private String baseUrl;

    private final OkHttpClient client;
    private final Gson gson;

    // Map our categories to News API categories
    private static final Map<String, String> CATEGORY_MAPPING = new HashMap<>();

    static {
        CATEGORY_MAPPING.put("SPORTS", "sports");
        CATEGORY_MAPPING.put("NEWS", "general");
        CATEGORY_MAPPING.put("TECHNOLOGY", "tech");
        CATEGORY_MAPPING.put("FINANCE", "business");
        CATEGORY_MAPPING.put("ENTERTAINMENT", "entertainment");
        CATEGORY_MAPPING.put("HEALTH", "health");
        CATEGORY_MAPPING.put("TRAVEL", "travel");
        CATEGORY_MAPPING.put("EDUCATION", "science");
        CATEGORY_MAPPING.put("WEATHER", "general");
        CATEGORY_MAPPING.put("SHOPPING", "business");
        CATEGORY_MAPPING.put("SOCIAL", "general");
        CATEGORY_MAPPING.put("PROMOTIONS", "business");
    }

    public NewsApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public List<NewsArticle> fetchNewsForCategory(String category) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            String apiCategory = CATEGORY_MAPPING.getOrDefault(category.toUpperCase(), "general");
            
            HttpUrl.Builder httpBuilder = HttpUrl.parse(baseUrl).newBuilder();
            httpBuilder.addQueryParameter("api_token", apiToken);
            httpBuilder.addQueryParameter("categories", apiCategory);
            httpBuilder.addQueryParameter("language", "en");
            httpBuilder.addQueryParameter("limit", "5");

            if (category.equalsIgnoreCase("WEATHER")) {
                httpBuilder.addQueryParameter("search", "weather forecast");
            } else if (category.equalsIgnoreCase("SHOPPING")) {
                httpBuilder.addQueryParameter("search", "shopping deals retail");
            } else if (category.equalsIgnoreCase("PROMOTIONS")) {
                httpBuilder.addQueryParameter("search", "deals offers sale");
            }

            Request request = new Request.Builder()
                    .url(httpBuilder.build())
                    .build();

            logger.debug("Fetching news for category: {} (API category: {})", category, apiCategory);

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    articles = parseNewsResponse(jsonResponse, category);
                    logger.info("Fetched {} articles for category: {}", articles.size(), category);
                } else {
                    logger.warn("News API returned non-success status: {} for category: {}", 
                            response.code(), category);
                }
            }

        } catch (IOException e) {
            logger.error("Failed to fetch news for category: {} - {}", category, e.getMessage());
        }

        return articles;
    }

    public Map<String, List<NewsArticle>> fetchNewsForCategories(List<String> categories) {
        Map<String, List<NewsArticle>> newsByCategory = new HashMap<>();
        
        for (String category : categories) {
            List<NewsArticle> articles = fetchNewsForCategory(category);
            if (!articles.isEmpty()) {
                newsByCategory.put(category.toUpperCase(), articles);
            }
        }
        
        return newsByCategory;
    }

    private List<NewsArticle> parseNewsResponse(String jsonResponse, String category) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray data = root.getAsJsonArray("data");
            
            if (data != null) {
                for (JsonElement element : data) {
                    JsonObject articleJson = element.getAsJsonObject();
                    
                    NewsArticle article = new NewsArticle();
                    article.setCategory(category.toUpperCase());
                    article.setTitle(getStringOrNull(articleJson, "title"));
                    article.setDescription(getStringOrNull(articleJson, "description"));
                    article.setUrl(getStringOrNull(articleJson, "url"));
                    article.setSource(getStringOrNull(articleJson, "source"));
                    article.setPublishedAt(getStringOrNull(articleJson, "published_at"));
                    article.setImageUrl(getStringOrNull(articleJson, "image_url"));
                    
                    if (article.getTitle() != null) {
                        articles.add(article);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse news response: {}", e.getMessage());
        }
        
        return articles;
    }

    private String getStringOrNull(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    public static class NewsArticle {
        private String category;
        private String title;
        private String description;
        private String url;
        private String source;
        private String publishedAt;
        private String imageUrl;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
        
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String toNotificationMessage() {
            StringBuilder msg = new StringBuilder();
            msg.append("📰 ").append(title).append("\n\n");
            
            if (description != null && !description.isEmpty()) {
                String desc = description.length() > 200 
                    ? description.substring(0, 200) + "..." 
                    : description;
                msg.append(desc).append("\n\n");
            }
            
            if (source != null) {
                msg.append("📌 Source: ").append(source).append("\n");
            }
            
            if (url != null) {
                msg.append("🔗 Read more: ").append(url);
            }
            
            return msg.toString();
        }
    }
}
