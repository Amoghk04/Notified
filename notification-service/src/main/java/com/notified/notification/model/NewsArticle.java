package com.notified.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "#{@newsArticleCollectionNameProvider.getCollectionName()}")
public class NewsArticle {

    @Id
    private String id;

    private String category;

    private String title;

    private String description;

    private String link;

    @Indexed(unique = true)
    private String contentHash;  // Hash of title+link to prevent duplicates

    private String source;

    private LocalDateTime publishedDate;

    private LocalDateTime scrapedAt;

    // Constructors
    public NewsArticle() {
        this.scrapedAt = LocalDateTime.now();
    }

    public NewsArticle(String category, String title, String description, String link, String source) {
        this();
        this.category = category;
        this.title = title;
        this.description = description;
        this.link = link;
        this.source = source;
        this.contentHash = generateHash(title, link);
    }

    private String generateHash(String title, String link) {
        return Integer.toHexString((title + link).hashCode());
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        if (this.link != null) {
            this.contentHash = generateHash(title, this.link);
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
        if (this.title != null) {
            this.contentHash = generateHash(this.title, link);
        }
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDateTime publishedDate) {
        this.publishedDate = publishedDate;
    }

    public LocalDateTime getScrapedAt() {
        return scrapedAt;
    }

    public void setScrapedAt(LocalDateTime scrapedAt) {
        this.scrapedAt = scrapedAt;
    }
}
