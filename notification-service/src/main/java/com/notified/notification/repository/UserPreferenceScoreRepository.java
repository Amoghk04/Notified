package com.notified.notification.repository;

import com.notified.notification.model.UserPreferenceScore;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferenceScoreRepository extends MongoRepository<UserPreferenceScore, String> {
    
    Optional<UserPreferenceScore> findByUserId(String userId);
    
    boolean existsByUserId(String userId);
}
