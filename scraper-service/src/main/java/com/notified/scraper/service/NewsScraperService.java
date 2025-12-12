package com.notified.scraper.service;

import com.notified.scraper.config.NewsArticleCollectionNameProvider;
import com.notified.scraper.model.NewsArticle;
import com.notified.scraper.repository.NewsArticleRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class NewsScraperService {

    private static final Logger logger = LoggerFactory.getLogger(NewsScraperService.class);

    private final NewsArticleRepository newsArticleRepository;
    private final NewsArticleCollectionNameProvider collectionNameProvider;

    @Value("${scraper.article-retention-days:3}")
    private int articleRetentionDays;

    // RSS feeds mapped to categories
    private static final Map<String, List<String>> CATEGORY_FEEDS = new HashMap<>();

    static {
        // Sports
        CATEGORY_FEEDS.put("SPORTS", Arrays.asList(
            "https://feeds.bbci.co.uk/sport/rss.xml",
            "https://www.espn.com/espn/rss/news",
            "https://timesofindia.indiatimes.com/rssfeeds/4719148.cms",
            "https://www.hindustantimes.com/feeds/rss/sports/rssfeed.xml",
            "https://indianexpress.com/section/sports/feed/"
        ));
        
        CATEGORY_FEEDS.put("NEWS", Arrays.asList(
            "https://feeds.bbci.co.uk/news/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeedstopstories.cms",
            "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml",
            "https://indianexpress.com/section/india/feed/",
            "https://www.thehindu.com/news/national/feeder/default.rss"
        ));
        
        CATEGORY_FEEDS.put("TECHNOLOGY", Arrays.asList(
            "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/66949542.cms",
            "https://indianexpress.com/section/technology/feed/",
            "https://www.theverge.com/rss/index.xml"
        ));
        
        CATEGORY_FEEDS.put("FINANCE", Arrays.asList(
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms",
            "https://www.hindustantimes.com/feeds/rss/business/rssfeed.xml",
            "https://indianexpress.com/section/business/feed/",
            "https://economictimes.indiatimes.com/rssfeedstopstories.cms"
        ));
        
        CATEGORY_FEEDS.put("ENTERTAINMENT", Arrays.asList(
            "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms",
            "https://www.hindustantimes.com/feeds/rss/entertainment/rssfeed.xml",
            "https://indianexpress.com/section/entertainment/feed/"
        ));
        
        CATEGORY_FEEDS.put("HEALTH", Arrays.asList(
            "https://feeds.bbci.co.uk/news/health/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/3908999.cms",
            "https://indianexpress.com/section/lifestyle/health/feed/"
        ));
        
        CATEGORY_FEEDS.put("TRAVEL", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/1977021.cms",
            "https://indianexpress.com/section/lifestyle/destination-of-the-week/feed/"
        ));
        
        CATEGORY_FEEDS.put("EDUCATION", Arrays.asList(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/913168846.cms",
            "https://indianexpress.com/section/education/feed/"
        ));
        
        CATEGORY_FEEDS.put("WEATHER", Arrays.asList(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://timesofindia.indiatimes.com/rssfeeds/2647163.cms"
        ));
        
        CATEGORY_FEEDS.put("SOCIAL", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/2886704.cms",
            "https://indianexpress.com/section/lifestyle/feed/",
            "https://www.hindustantimes.com/feeds/rss/lifestyle/rssfeed.xml"
        ));

        CATEGORY_FEEDS.put("SHOPPING", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
        ));

        CATEGORY_FEEDS.put("PROMOTIONS", Arrays.asList(
            "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
        ));
    }

    public NewsScraperService(NewsArticleRepository newsArticleRepository,
                             NewsArticleCollectionNameProvider collectionNameProvider) {
        this.newsArticleRepository = newsArticleRepository;
        this.collectionNameProvider = collectionNameProvider;
    }

    /**
     * Get all available categories
     */
    public Set<String> getAvailableCategories() {
        return CATEGORY_FEEDS.keySet();
    }

    @Scheduled(fixedRateString = "${scraper.schedule.scrape-interval-ms:600000}")
    public void scrapeAllCategories() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("═══════════════════════════════════════════════════════════════════");
        logger.info("🚀 SCRAPING JOB STARTED at {}", startTime);
        logger.info("═══════════════════════════════════════════════════════════════════");
        
        int totalCategories = CATEGORY_FEEDS.size();
        int successfulCategories = 0;
        int failedCategories = 0;
        int totalArticlesScraped = 0;
        int totalDuplicatesSkipped = 0;
        
        for (String category : CATEGORY_FEEDS.keySet()) {
            try {
                logger.info("───────────────────────────────────────────────────────────────────");
                logger.info("📂 Processing category: {} ({} feeds configured)", 
                    category, CATEGORY_FEEDS.get(category).size());
                
                int[] results = scrapeCategoryNewsWithStats(category);
                totalArticlesScraped += results[0];
                totalDuplicatesSkipped += results[1];
                successfulCategories++;
                
            } catch (Exception e) {
                logger.error("❌ Error scraping category: {} - {}", category, e.getMessage(), e);
                failedCategories++;
            }
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        
        logger.info("═══════════════════════════════════════════════════════════════════");
        logger.info("✅ SCRAPING JOB COMPLETED at {}", endTime);
        logger.info("📊 SUMMARY:");
        logger.info("   • Duration: {} seconds", durationSeconds);
        logger.info("   • Categories processed: {}/{} successful, {} failed", 
            successfulCategories, totalCategories, failedCategories);
        logger.info("   • New articles scraped: {}", totalArticlesScraped);
        logger.info("   • Duplicates skipped: {}", totalDuplicatesSkipped);
        logger.info("   • Next scrape scheduled in 30 minutes");
        logger.info("═══════════════════════════════════════════════════════════════════");
    }

    /**
     * Scrape a category and return stats [newArticles, duplicates]
     */
    private int[] scrapeCategoryNewsWithStats(String category) {
        String collectionName = category.toLowerCase() + "_notifications";
        collectionNameProvider.setCollectionName(collectionName);
        
        int totalScraped = 0;
        int duplicates = 0;
        
        try {
            List<String> feeds = CATEGORY_FEEDS.get(category);
            if (feeds == null || feeds.isEmpty()) {
                logger.warn("⚠️ No feeds configured for category: {}", category);
                return new int[]{0, 0};
            }

            for (String feedUrl : feeds) {
                logger.debug("   🔗 Fetching feed: {}", feedUrl);
                long feedStartTime = System.currentTimeMillis();
                
                try {
                    List<NewsArticle> articles = fetchArticlesFromFeed(feedUrl, category);
                    int feedNewArticles = 0;
                    int feedDuplicates = 0;
                    
                    for (NewsArticle article : articles) {
                        // Check for duplicates
                        if (!newsArticleRepository.existsByContentHash(article.getContentHash())) {
                            newsArticleRepository.save(article);
                            feedNewArticles++;
                            totalScraped++;
                        } else {
                            feedDuplicates++;
                            duplicates++;
                        }
                    }
                    
                    long feedDuration = System.currentTimeMillis() - feedStartTime;
                    logger.debug("   ✓ Feed completed in {}ms - {} articles fetched, {} new, {} duplicates", 
                        feedDuration, articles.size(), feedNewArticles, feedDuplicates);
                    
                } catch (Exception e) {
                    long feedDuration = System.currentTimeMillis() - feedStartTime;
                    logger.warn("   ✗ Feed FAILED after {}ms: {} - {}", feedDuration, feedUrl, e.getMessage());
                }
            }

            logger.info("📂 Category {} completed: {} new articles, {} duplicates (Collection: {})",
                    category, totalScraped, duplicates, collectionName);

        } finally {
            collectionNameProvider.clearCollectionName();
        }
        
        return new int[]{totalScraped, duplicates};
    }

    public void scrapeCategoryNews(String category) {
        scrapeCategoryNewsWithStats(category);
    }

    private List<NewsArticle> fetchArticlesFromFeed(String feedUrl, String category) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            URL url = new URL(feedUrl);
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(url));

            String sourceName = feed.getTitle() != null ? feed.getTitle() : feedUrl;

            for (SyndEntry entry : feed.getEntries()) {
                NewsArticle article = new NewsArticle();
                article.setCategory(category);
                article.setTitle(entry.getTitle() != null ? entry.getTitle() : "No Title");
                
                String description = "";
                if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
                    description = entry.getDescription().getValue()
                            .replaceAll("<[^>]*>", "")  // Remove HTML tags
                            .trim();
                    if (description.length() > 500) {
                        description = description.substring(0, 497) + "...";
                    }
                }
                article.setDescription(description);
                
                article.setLink(entry.getLink() != null ? entry.getLink() : "");
                article.setSource(sourceName);
                
                if (entry.getPublishedDate() != null) {
                    article.setPublishedDate(
                        entry.getPublishedDate().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    );
                }
                
                articles.add(article);
            }

        } catch (Exception e) {
            logger.error("Error fetching articles from feed: {}", feedUrl, e);
        }

        return articles;
    }

    // Cleanup old articles (runs daily at 2 AM)
    @Scheduled(cron = "${scraper.schedule.cleanup-cron:0 0 2 * * *}")
    public void cleanupOldArticles() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("═══════════════════════════════════════════════════════════════════");
        logger.info("🧹 CLEANUP JOB STARTED at {}", startTime);
        logger.info("═══════════════════════════════════════════════════════════════════");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(articleRetentionDays);
        logger.info("   Removing articles older than: {} ({} days retention)", cutoffDate, articleRetentionDays);
        
        int totalDeleted = 0;
        int categoriesProcessed = 0;
        
        for (String category : CATEGORY_FEEDS.keySet()) {
            String collectionName = category.toLowerCase() + "_notifications";
            collectionNameProvider.setCollectionName(collectionName);
            
            try {
                List<NewsArticle> oldArticles = newsArticleRepository.findByScrapedAtBefore(cutoffDate);
                int deletedCount = oldArticles.size();
                
                if (deletedCount > 0) {
                    newsArticleRepository.deleteAll(oldArticles);
                    logger.info("   🗑️ {} - Deleted {} old articles", category, deletedCount);
                } else {
                    logger.debug("   ✓ {} - No old articles to delete", category);
                }
                
                totalDeleted += deletedCount;
                categoriesProcessed++;
                
            } catch (Exception e) {
                logger.error("   ❌ Error cleaning up category {}: {}", category, e.getMessage(), e);
            } finally {
                collectionNameProvider.clearCollectionName();
            }
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        
        logger.info("═══════════════════════════════════════════════════════════════════");
        logger.info("✅ CLEANUP JOB COMPLETED at {}", endTime);
        logger.info("📊 SUMMARY:");
        logger.info("   • Duration: {} seconds", durationSeconds);
        logger.info("   • Categories processed: {}", categoriesProcessed);
        logger.info("   • Total articles deleted: {}", totalDeleted);
        logger.info("═══════════════════════════════════════════════════════════════════");
    }

    public List<NewsArticle> getArticlesByCategory(String category, int limit) {
        String collectionName = category.toLowerCase() + "_notifications";
        collectionNameProvider.setCollectionName(collectionName);
        
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return newsArticleRepository.findByCategoryOrderByPublishedDateDesc(category, pageable);
        } finally {
            collectionNameProvider.clearCollectionName();
        }
    }

    /**
     * Get paginated articles for a category (for browsing)
     * @param category The category name
     * @param page Page number (0-indexed)
     * @param pageSize Number of articles per page
     * @return List of articles for the requested page
     */
    public List<NewsArticle> getArticlesByCategoryPaginated(String category, int page, int pageSize) {
        String collectionName = category.toLowerCase() + "_notifications";
        collectionNameProvider.setCollectionName(collectionName);
        
        try {
            Pageable pageable = PageRequest.of(page, pageSize);
            return newsArticleRepository.findByCategoryOrderByPublishedDateDesc(category.toUpperCase(), pageable);
        } finally {
            collectionNameProvider.clearCollectionName();
        }
    }

    /**
     * Count total articles in a category
     * @param category The category name
     * @return Total number of articles
     */
    public long countArticlesByCategory(String category) {
        String collectionName = category.toLowerCase() + "_notifications";
        collectionNameProvider.setCollectionName(collectionName);
        
        try {
            return newsArticleRepository.countByCategory(category.toUpperCase());
        } finally {
            collectionNameProvider.clearCollectionName();
        }
    }
}
