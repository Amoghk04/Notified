package com.notified.notification.client;

import com.notified.notification.model.UserPreference;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "user-preference-service")
public interface UserPreferenceClient {

    @GetMapping("/preferences/{userId}")
    UserPreference getUserPreference(@PathVariable("userId") String userId);

    @GetMapping("/preferences")
    List<UserPreference> getAllPreferences();
}
