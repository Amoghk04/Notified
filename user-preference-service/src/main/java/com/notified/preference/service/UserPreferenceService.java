package com.notified.preference.service;

import com.notified.preference.model.UserPreference;
import com.notified.preference.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository repository;

    public UserPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    public List<UserPreference> getAllPreferences() {
        return repository.findAll();
    }

    public Optional<UserPreference> getPreferenceByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    public UserPreference createPreference(UserPreference preference) {
        return repository.save(preference);
    }

    public UserPreference updatePreference(String userId, UserPreference preference) {
        Optional<UserPreference> existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            UserPreference existingPref = existing.get();
            existingPref.setEmail(preference.getEmail());
            existingPref.setPhoneNumber(preference.getPhoneNumber());
            existingPref.setTelegramChatId(preference.getTelegramChatId());
            existingPref.setEnabledChannels(preference.getEnabledChannels());
            existingPref.setPreferences(preference.getPreferences());
            existingPref.setPreference(preference.getPreference());
            // Update notification interval if provided
            if (preference.getNotificationIntervalMinutes() != null) {
                existingPref.setNotificationIntervalMinutes(preference.getNotificationIntervalMinutes());
            }
            // Update lastNotificationSent if provided
            if (preference.getLastNotificationSent() != null) {
                existingPref.setLastNotificationSent(preference.getLastNotificationSent());
            }
            return repository.save(existingPref);
        } else {
            preference.setUserId(userId);
            return repository.save(preference);
        }
    }

    public void deletePreference(String userId) {
        repository.findByUserId(userId).ifPresent(repository::delete);
    }
}
