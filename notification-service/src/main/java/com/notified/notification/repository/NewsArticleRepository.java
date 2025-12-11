package com.notified.notification.repository;

import com.notified.notification.model.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsArticleRepository extends MongoRepository<NewsArticle, String> {
    
    List<NewsArticle> findByCategory(String category);
    
    List<NewsArticle> findByCategoryOrderByScrapedAtDesc(String category);
    
    List<NewsArticle> findByCategoryOrderByPublishedDateDesc(String category, Pageable pageable);
    
    boolean existsByContentHash(String contentHash);
    
    void deleteByScrapedAtBefore(LocalDateTime cutoffDate);
    
    List<NewsArticle> findByScrapedAtBefore(LocalDateTime cutoffDate);
}
