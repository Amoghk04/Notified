package com.notified.notification.repository;

import com.notified.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByUserId(String userId);
    
    boolean existsByUserIdAndArticleContentHash(String userId, String articleContentHash);
    
    Optional<Notification> findByTelegramMessageIdAndUserId(Long telegramMessageId, String visitorId);
}
