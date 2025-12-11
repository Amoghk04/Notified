package com.notified.notification.service;

import com.notified.notification.config.NewsArticleCollectionNameProvider;
import com.notified.notification.model.NewsArticle;
import com.notified.notification.repository.NewsArticleRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Scheduled(fixedRate = 600000) // Run every 10 minutes (600,000 ms)
    public void scrapeAllCategories() {
        logger.info("Starting scheduled news scraping for all categories...");
        
        for (String category : CATEGORY_FEEDS.keySet()) {
            try {
                scrapeCategoryNews(category);
            } catch (Exception e) {
                logger.error("Error scraping category: {}", category, e);
            }
        }
        
        logger.info("Completed news scraping for all categories");
    }

    public void scrapeCategoryNews(String category) {
        String collectionName = category.toLowerCase() + "_notifications";
        collectionNameProvider.setCollectionName(collectionName);
        
        try {
            List<String> feeds = CATEGORY_FEEDS.get(category);
            if (feeds == null || feeds.isEmpty()) {
                logger.warn("No feeds configured for category: {}", category);
                return;
            }

            int totalScraped = 0;
            int duplicates = 0;

            for (String feedUrl : feeds) {
                try {
                    List<NewsArticle> articles = fetchArticlesFromFeed(feedUrl, category);
                    
                    for (NewsArticle article : articles) {
                        // Check for duplicates
                        if (!newsArticleRepository.existsByContentHash(article.getContentHash())) {
                            newsArticleRepository.save(article);
                            totalScraped++;
                        } else {
                            duplicates++;
                        }
                    }
                    
                } catch (Exception e) {
                    logger.warn("Failed to fetch from feed: {} - {}", feedUrl, e.getMessage());
                }
            }

            logger.info("Category: {} - Scraped: {} new articles, Skipped: {} duplicates (Collection: {})",
                    category, totalScraped, duplicates, collectionName);

        } finally {
            collectionNameProvider.clearCollectionName();
        }
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
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldArticles() {
        logger.info("Starting cleanup of old news articles...");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(3);
        
        for (String category : CATEGORY_FEEDS.keySet()) {
            String collectionName = category.toLowerCase() + "_notifications";
            collectionNameProvider.setCollectionName(collectionName);
            
            try {
                List<NewsArticle> oldArticles = newsArticleRepository.findByScrapedAtBefore(cutoffDate);
                newsArticleRepository.deleteAll(oldArticles);
                logger.info("Deleted {} old articles from {} (older than {})", 
                    oldArticles.size(), collectionName, cutoffDate);
            } catch (Exception e) {
                logger.error("Error cleaning up category: {}", category, e);
            } finally {
                collectionNameProvider.clearCollectionName();
            }
        }
        
        logger.info("Completed cleanup of old articles");
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
}
