package com.notified.preference.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notified.preference.model.UserPreference;
import com.notified.preference.service.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserPreferenceController.class)
@DisplayName("UserPreferenceController Tests")
class UserPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferenceService service;

    @Autowired
    private ObjectMapper objectMapper;

    private UserPreference testUser1;
    private UserPreference testUser2;

    @BeforeEach
    void setUp() {
        // Set up test user 1
        testUser1 = new UserPreference();
        testUser1.setId("1");
        testUser1.setUserId("user123");
        testUser1.setEmail("test@example.com");
        testUser1.setTelegramChatId("123456789");
        testUser1.setPreferences(Arrays.asList("SPORTS", "TECHNOLOGY"));
        Set<UserPreference.NotificationChannel> channels1 = new HashSet<>();
        channels1.add(UserPreference.NotificationChannel.EMAIL);
        channels1.add(UserPreference.NotificationChannel.TELEGRAM);
        testUser1.setEnabledChannels(channels1);
        testUser1.setNotificationIntervalMinutes(60);

        // Set up test user 2
        testUser2 = new UserPreference();
        testUser2.setId("2");
        testUser2.setUserId("user456");
        testUser2.setEmail("test2@example.com");
        testUser2.setTelegramChatId("987654321");
        testUser2.setPreferences(Arrays.asList("NEWS", "ENTERTAINMENT"));
        Set<UserPreference.NotificationChannel> channels2 = new HashSet<>();
        channels2.add(UserPreference.NotificationChannel.TELEGRAM);
        testUser2.setEnabledChannels(channels2);
        testUser2.setNotificationIntervalMinutes(1440);
    }

    @Test
    @DisplayName("GET /preferences - Should return all preferences")
    void getAllPreferences_ShouldReturnAllUsers() throws Exception {
        List<UserPreference> users = Arrays.asList(testUser1, testUser2);
        when(service.getAllPreferences()).thenReturn(users);

        mockMvc.perform(get("/preferences"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value("user123"))
                .andExpect(jsonPath("$[1].userId").value("user456"));

        verify(service, times(1)).getAllPreferences();
    }

    @Test
    @DisplayName("GET /preferences - Should return empty list when no users")
    void getAllPreferences_ShouldReturnEmptyList() throws Exception {
        when(service.getAllPreferences()).thenReturn(List.of());

        mockMvc.perform(get("/preferences"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /preferences/{userId} - Should return user when found")
    void getPreferenceByUserId_WhenFound_ShouldReturnUser() throws Exception {
        when(service.getPreferenceByUserId("user123")).thenReturn(Optional.of(testUser1));

        mockMvc.perform(get("/preferences/user123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.telegramChatId").value("123456789"))
                .andExpect(jsonPath("$.preferences.length()").value(2));
    }

    @Test
    @DisplayName("GET /preferences/{userId} - Should return 404 when not found")
    void getPreferenceByUserId_WhenNotFound_ShouldReturn404() throws Exception {
        when(service.getPreferenceByUserId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/preferences/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /preferences - Should create new preference")
    void createPreference_ShouldReturnCreatedUser() throws Exception {
        when(service.createPreference(any(UserPreference.class))).thenReturn(testUser1);

        mockMvc.perform(post("/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(service, times(1)).createPreference(any(UserPreference.class));
    }

    @Test
    @DisplayName("POST /preferences - Should return 400 for invalid data (missing userId)")
    void createPreference_WithInvalidData_ShouldReturn400() throws Exception {
        UserPreference invalidUser = new UserPreference();
        invalidUser.setEmail("test@example.com");
        // Missing required userId

        mockMvc.perform(post("/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /preferences - Should return 400 for invalid email")
    void createPreference_WithInvalidEmail_ShouldReturn400() throws Exception {
        UserPreference invalidUser = new UserPreference();
        invalidUser.setUserId("testuser");
        invalidUser.setEmail("invalid-email"); // Invalid email format

        mockMvc.perform(post("/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /preferences/{userId} - Should update existing preference")
    void updatePreference_ShouldReturnUpdatedUser() throws Exception {
        UserPreference updatedUser = new UserPreference();
        updatedUser.setUserId("user123");
        updatedUser.setEmail("updated@example.com");
        updatedUser.setTelegramChatId("123456789");
        updatedUser.setPreferences(Arrays.asList("SPORTS", "NEWS", "FINANCE"));
        updatedUser.setNotificationIntervalMinutes(120);

        when(service.updatePreference(eq("user123"), any(UserPreference.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/preferences/user123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.preferences.length()").value(3))
                .andExpect(jsonPath("$.notificationIntervalMinutes").value(120));

        verify(service, times(1)).updatePreference(eq("user123"), any(UserPreference.class));
    }

    @Test
    @DisplayName("DELETE /preferences/{userId} - Should delete preference")
    void deletePreference_ShouldReturn204() throws Exception {
        doNothing().when(service).deletePreference("user123");

        mockMvc.perform(delete("/preferences/user123"))
                .andExpect(status().isNoContent());

        verify(service, times(1)).deletePreference("user123");
    }

    @Test
    @DisplayName("GET /preferences/{userId} - Should return correct enabled channels")
    void getPreference_ShouldReturnCorrectChannels() throws Exception {
        when(service.getPreferenceByUserId("user123")).thenReturn(Optional.of(testUser1));

        mockMvc.perform(get("/preferences/user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledChannels").isArray())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.telegramEnabled").value(true));
    }
}
