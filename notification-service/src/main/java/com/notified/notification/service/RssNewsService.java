package com.notified.notification.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RssNewsService {

    private static final Logger logger = LoggerFactory.getLogger(RssNewsService.class);

    // RSS feeds mapped to our categories - ALL FREE!
    private static final Map<String, List<String>> CATEGORY_FEEDS = new HashMap<>();

    static {
        // Sports - Including Indian sources
        CATEGORY_FEEDS.put("SPORTS", Arrays.asList(
            "https://feeds.bbci.co.uk/sport/rss.xml",
            "https://www.espn.com/espn/rss/news",
            "https://timesofindia.indiatimes.com/rssfeeds/4719148.cms",  // TOI Sports
            "https://www.hindustantimes.com/feeds/rss/sports/rssfeed.xml",  // HT Sports
            "https://indianexpress.com/section/sports/feed/"  // Indian Express Sports
        ));
        
        // General News - Including Indian sources
        CATEGORY_FEEDS.put("NEWS", Arrays.asList(
            "https://feeds.bbci.co.uk/news/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeedstopstories.cms",  // TOI Top Stories
            "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml",  // HT India
            "https://indianexpress.com/section/india/feed/",  // Indian Express India
            "https://www.thehindu.com/news/national/feeder/default.rss"  // The Hindu National
        ));
        
        // Technology - Including Indian sources
        CATEGORY_FEEDS.put("TECHNOLOGY", Arrays.asList(
            "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/66949542.cms",  // TOI Tech
            "https://indianexpress.com/section/technology/feed/",  // Indian Express Tech
            "https://www.theverge.com/rss/index.xml"
        ));
        
        // Finance/Business - Including Indian sources
        CATEGORY_FEEDS.put("FINANCE", Arrays.asList(
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms",  // TOI Business
            "https://www.hindustantimes.com/feeds/rss/business/rssfeed.xml",  // HT Business
            "https://indianexpress.com/section/business/feed/",  // Indian Express Business
            "https://economictimes.indiatimes.com/rssfeedstopstories.cms"  // Economic Times
        ));
        
        // Entertainment - Including Indian/Bollywood sources
        CATEGORY_FEEDS.put("ENTERTAINMENT", Arrays.asList(
            "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms",  // TOI Entertainment
            "https://www.hindustantimes.com/feeds/rss/entertainment/rssfeed.xml",  // HT Entertainment
            "https://indianexpress.com/section/entertainment/feed/"  // Indian Express Entertainment
        ));
        
        // Health - Including Indian sources
        CATEGORY_FEEDS.put("HEALTH", Arrays.asList(
            "https://feeds.bbci.co.uk/news/health/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/3908999.cms",  // TOI Health
            "https://indianexpress.com/section/lifestyle/health/feed/"  // Indian Express Health
        ));
        
        // Travel - Including Indian sources
        CATEGORY_FEEDS.put("TRAVEL", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/1977021.cms",  // TOI Travel
            "https://indianexpress.com/section/lifestyle/destination-of-the-week/feed/"
        ));
        
        // Education/Science - Including Indian sources
        CATEGORY_FEEDS.put("EDUCATION", Arrays.asList(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/913168846.cms",  // TOI Education
            "https://indianexpress.com/section/education/feed/"  // Indian Express Education
        ));
        
        // Weather - Environment news
        CATEGORY_FEEDS.put("WEATHER", Arrays.asList(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/2647163.cms"  // TOI Environment
        ));
        
        // Social - Lifestyle/Trending
        CATEGORY_FEEDS.put("SOCIAL", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/2886704.cms",  // TOI Life & Style
            "https://indianexpress.com/section/lifestyle/feed/",
            "https://www.hindustantimes.com/feeds/rss/lifestyle/rssfeed.xml"
        ));
        
        // Shopping/Promotions - Business/Consumer news
        CATEGORY_FEEDS.put("SHOPPING", Arrays.asList(
            "https://economictimes.indiatimes.com/rssfeedstopstories.cms",
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
        ));
        
        CATEGORY_FEEDS.put("PROMOTIONS", Arrays.asList(
            "https://economictimes.indiatimes.com/rssfeedstopstories.cms",
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
        ));
    }

    // Simple cache to avoid hammering RSS feeds
    private final Map<String, CachedNews> newsCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000; // 10 minutes

    // Preset categories list for checking if topic is custom
    private static final Set<String> PRESET_CATEGORIES = new HashSet<>(Arrays.asList(
        "SPORTS", "NEWS", "WEATHER", "SHOPPING", "FINANCE", 
        "ENTERTAINMENT", "HEALTH", "TECHNOLOGY", "TRAVEL", 
        "SOCIAL", "EDUCATION", "PROMOTIONS"
    ));

    public List<NewsArticle> fetchNewsForCategory(String category) {
        String upperCategory = category.toUpperCase().trim();
        
        // Check cache first
        CachedNews cached = newsCache.get(upperCategory);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Returning cached news for category: {}", upperCategory);
            return cached.articles;
        }

        List<NewsArticle> articles = new ArrayList<>();
        
        // Check if this is a preset category or custom topic
        if (PRESET_CATEGORIES.contains(upperCategory)) {
            // Use predefined RSS feeds for preset categories
            List<String> feedUrls = CATEGORY_FEEDS.get(upperCategory);
            for (String feedUrl : feedUrls) {
                try {
                    articles.addAll(fetchFromFeed(feedUrl, upperCategory));
                    if (articles.size() >= 5) break;
                } catch (Exception e) {
                    logger.warn("Failed to fetch RSS feed: {} - {}", feedUrl, e.getMessage());
                }
            }
        } else {
            // Custom topic - use Google News RSS search
            articles = fetchCustomTopicNews(upperCategory);
        }

        // Cache the results
        if (!articles.isEmpty()) {
            newsCache.put(upperCategory, new CachedNews(articles));
        }

        logger.info("Fetched {} articles for {}: {}", articles.size(), 
            PRESET_CATEGORIES.contains(upperCategory) ? "category" : "custom topic", upperCategory);
        return articles;
    }

    /**
     * Fetch news for custom topics using Google News RSS search
     * Uses exact phrase matching for better relevance
     */
    private List<NewsArticle> fetchCustomTopicNews(String topic) {
        List<NewsArticle> articles = new ArrayList<>();
        String originalTopic = topic;
        
        try {
            // For multi-word topics, use exact phrase matching with quotes
            String searchTerm = topic.trim();
            if (searchTerm.contains(" ")) {
                // Wrap in quotes for exact phrase matching
                searchTerm = "\"" + searchTerm + "\"";
            }
            
            // Google News RSS search URL - works for any keyword!
            String searchQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
            String googleNewsUrl = "https://news.google.com/rss/search?q=" + searchQuery + "&hl=en-IN&gl=IN&ceid=IN:en";
            
            logger.info("Searching Google News for custom topic: {} (query: {})", originalTopic, searchTerm);
            
            URL url = new URL(googleNewsUrl);
            SyndFeedInput input = new SyndFeedInput();
            
            try (XmlReader reader = new XmlReader(url)) {
                SyndFeed feed = input.build(reader);
                
                // Keywords for relevance filtering
                String[] topicWords = originalTopic.toLowerCase().split("\\s+");
                
                int count = 0;
                for (SyndEntry entry : feed.getEntries()) {
                    if (count >= 5) break; // Limit 5 articles for custom topics
                    
                    String title = entry.getTitle();
                    String description = entry.getDescription() != null ? 
                        entry.getDescription().getValue() : "";
                    
                    // Relevance check: ensure at least one keyword from topic appears in title or description
                    String lowerTitle = title != null ? title.toLowerCase() : "";
                    String lowerDesc = description.toLowerCase();
                    
                    boolean isRelevant = false;
                    for (String word : topicWords) {
                        if (word.length() >= 3 && (lowerTitle.contains(word) || lowerDesc.contains(word))) {
                            isRelevant = true;
                            break;
                        }
                    }
                    
                    // Also check if the entire topic phrase appears
                    String lowerTopic = originalTopic.toLowerCase();
                    if (lowerTitle.contains(lowerTopic) || lowerDesc.contains(lowerTopic)) {
                        isRelevant = true;
                    }
                    
                    if (!isRelevant) {
                        logger.debug("Skipping irrelevant article: {}", title);
                        continue;
                    }
                    
                    NewsArticle article = new NewsArticle();
                    article.setCategory(originalTopic);
                    article.setTitle(title);
                    article.setDescription(description);
                    article.setUrl(entry.getLink());
                    article.setSource("Google News");
                    
                    // Try to extract actual source from title (Google News format: "Title - Source")
                    if (title != null && title.contains(" - ")) {
                        int lastDash = title.lastIndexOf(" - ");
                        if (lastDash > 0) {
                            article.setSource(title.substring(lastDash + 3).trim());
                            article.setTitle(title.substring(0, lastDash).trim());
                        }
                    }
                    
                    if (entry.getPublishedDate() != null) {
                        article.setPublishedAt(entry.getPublishedDate().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime());
                    }
                    
                    articles.add(article);
                    count++;
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to fetch custom topic news for '{}': {}", topic, e.getMessage());
        }
        
        return articles;
    }

    private List<NewsArticle> fetchFromFeed(String feedUrl, String category) throws Exception {
        List<NewsArticle> articles = new ArrayList<>();
        
        URL url = new URL(feedUrl);
        SyndFeedInput input = new SyndFeedInput();
        
        try (XmlReader reader = new XmlReader(url)) {
            SyndFeed feed = input.build(reader);
            
            int count = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (count >= 3) break; // Limit 3 per feed
                
                NewsArticle article = new NewsArticle();
                article.setCategory(category);
                article.setTitle(entry.getTitle());
                article.setDescription(entry.getDescription() != null ? 
                    entry.getDescription().getValue() : null);
                article.setUrl(entry.getLink());
                article.setSource(feed.getTitle());
                
                // Try to get published date
                if (entry.getPublishedDate() != null) {
                    article.setPublishedAt(entry.getPublishedDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
                }
                
                // Try to extract image from enclosures or media
                if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                    String enclosureUrl = entry.getEnclosures().get(0).getUrl();
                    if (enclosureUrl != null && (enclosureUrl.contains(".jpg") || 
                        enclosureUrl.contains(".png") || enclosureUrl.contains(".jpeg") ||
                        enclosureUrl.contains("image"))) {
                        article.setImageUrl(enclosureUrl);
                    }
                }
                
                // Extract image from description if available
                if (article.getImageUrl() == null && article.getDescription() != null) {
                    article.setImageUrl(extractImageFromHtml(article.getDescription()));
                }
                
                articles.add(article);
                count++;
            }
        }
        
        return articles;
    }

    private String extractImageFromHtml(String html) {
        if (html == null) return null;
        
        // Simple regex to extract image URL from HTML
        int imgStart = html.indexOf("src=\"");
        if (imgStart != -1) {
            imgStart += 5;
            int imgEnd = html.indexOf("\"", imgStart);
            if (imgEnd != -1) {
                String imgUrl = html.substring(imgStart, imgEnd);
                if (imgUrl.startsWith("http") && (imgUrl.contains(".jpg") || 
                    imgUrl.contains(".png") || imgUrl.contains(".jpeg"))) {
                    return imgUrl;
                }
            }
        }
        return null;
    }

    public Map<String, List<NewsArticle>> fetchNewsForCategories(List<String> categories) {
        Map<String, List<NewsArticle>> result = new HashMap<>();
        
        for (String category : categories) {
            List<NewsArticle> articles = fetchNewsForCategory(category);
            if (!articles.isEmpty()) {
                result.put(category.toUpperCase(), articles);
            }
        }
        
        return result;
    }

    // Cache helper class
    private static class CachedNews {
        final List<NewsArticle> articles;
        final long timestamp;

        CachedNews(List<NewsArticle> articles) {
            this.articles = articles;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }

    // News article DTO
    public static class NewsArticle {
        private String category;
        private String title;
        private String description;
        private String url;
        private String source;
        private LocalDateTime publishedAt;
        private String imageUrl;

        // Getters and Setters
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
        
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String toNotificationMessage() {
            StringBuilder msg = new StringBuilder();
            
            // Title
            msg.append("📰 *").append(cleanHtml(title)).append("*\n\n");
            
            // Description (cleaned of HTML tags)
            if (description != null && !description.isEmpty()) {
                String cleanDesc = cleanHtml(description);
                if (cleanDesc.length() > 200) {
                    cleanDesc = cleanDesc.substring(0, 200) + "...";
                }
                msg.append(cleanDesc).append("\n\n");
            }
            
            // Source
            if (source != null) {
                msg.append("📌 *Source:* ").append(source).append("\n");
            }
            
            // Link
            if (url != null) {
                msg.append("🔗 ").append(url);
            }
            
            return msg.toString();
        }

        private String cleanHtml(String html) {
            if (html == null) return "";
            // Remove HTML tags
            return html.replaceAll("<[^>]*>", "")
                       .replaceAll("&nbsp;", " ")
                       .replaceAll("&amp;", "&")
                       .replaceAll("&lt;", "<")
                       .replaceAll("&gt;", ">")
                       .replaceAll("&quot;", "\"")
                       .replaceAll("&#39;", "'")
                       .trim();
        }
    }
}
