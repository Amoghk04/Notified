package com.notified.scraper.config;

import org.springframework.stereotype.Component;

@Component("newsArticleCollectionNameProvider")
public class NewsArticleCollectionNameProvider {
    
    private ThreadLocal<String> collectionName = new ThreadLocal<>();
    
    public String getCollectionName() {
        String name = collectionName.get();
        return name != null ? name : "news_notifications";
    }
    
    public void setCollectionName(String name) {
        collectionName.set(name);
    }
    
    public void clearCollectionName() {
        collectionName.remove();
    }
}
