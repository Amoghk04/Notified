package com.notified.preference.controller;

import com.notified.preference.model.UserPreference;
import com.notified.preference.service.UserPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/preferences")
public class UserPreferenceController {

    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<UserPreference>> getAllPreferences() {
        return ResponseEntity.ok(service.getAllPreferences());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserPreference> getPreferenceByUserId(@PathVariable String userId) {
        return service.getPreferenceByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserPreference> createPreference(@Valid @RequestBody UserPreference preference) {
        UserPreference created = service.createPreference(preference);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserPreference> updatePreference(
            @PathVariable String userId,
            @Valid @RequestBody UserPreference preference) {
        UserPreference updated = service.updatePreference(userId, preference);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deletePreference(@PathVariable String userId) {
        service.deletePreference(userId);
        return ResponseEntity.noContent().build();
    }
}
