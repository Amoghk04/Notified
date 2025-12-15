package com.notified.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notified.notification.model.Notification;
import com.notified.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService service;

    @Autowired
    private ObjectMapper objectMapper;

    private Notification testNotification1;
    private Notification testNotification2;

    @BeforeEach
    void setUp() {
        // Set up test notification 1
        testNotification1 = new Notification();
        testNotification1.setId("notif1");
        testNotification1.setUserId("user123");
        testNotification1.setMessage("Breaking news: Sports update");
        testNotification1.setSubject("Sports News");
        testNotification1.setStatus(Notification.NotificationStatus.SENT);
        testNotification1.setCreatedAt(LocalDateTime.now().minusHours(1));
        testNotification1.setSentAt(LocalDateTime.now());
        Set<Notification.NotificationChannel> channels1 = new HashSet<>();
        channels1.add(Notification.NotificationChannel.EMAIL);
        channels1.add(Notification.NotificationChannel.TELEGRAM);
        testNotification1.setChannels(channels1);

        // Set up test notification 2
        testNotification2 = new Notification();
        testNotification2.setId("notif2");
        testNotification2.setUserId("user123");
        testNotification2.setMessage("Tech news update");
        testNotification2.setSubject("Technology News");
        testNotification2.setStatus(Notification.NotificationStatus.PENDING);
        testNotification2.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /notifications - Should return all notifications")
    void getAllNotifications_ShouldReturnAllNotifications() throws Exception {
        List<Notification> notifications = Arrays.asList(testNotification1, testNotification2);
        when(service.getAllNotifications()).thenReturn(notifications);

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("notif1"))
                .andExpect(jsonPath("$[1].id").value("notif2"));

        verify(service, times(1)).getAllNotifications();
    }

    @Test
    @DisplayName("GET /notifications - Should return empty list when no notifications")
    void getAllNotifications_ShouldReturnEmptyList() throws Exception {
        when(service.getAllNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /notifications/user/{userId} - Should return user notifications")
    void getNotificationsByUserId_ShouldReturnUserNotifications() throws Exception {
        List<Notification> userNotifications = Arrays.asList(testNotification1, testNotification2);
        when(service.getNotificationsByUserId("user123")).thenReturn(userNotifications);

        mockMvc.perform(get("/notifications/user/user123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value("user123"))
                .andExpect(jsonPath("$[1].userId").value("user123"));

        verify(service, times(1)).getNotificationsByUserId("user123");
    }

    @Test
    @DisplayName("GET /notifications/user/{userId} - Should return empty list for unknown user")
    void getNotificationsByUserId_UnknownUser_ShouldReturnEmpty() throws Exception {
        when(service.getNotificationsByUserId("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/notifications/user/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /notifications/{id} - Should return notification when found")
    void getNotificationById_WhenFound_ShouldReturnNotification() throws Exception {
        when(service.getNotificationById("notif1")).thenReturn(Optional.of(testNotification1));

        mockMvc.perform(get("/notifications/notif1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("notif1"))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.message").value("Breaking news: Sports update"))
                .andExpect(jsonPath("$.subject").value("Sports News"))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    @DisplayName("GET /notifications/{id} - Should return 404 when not found")
    void getNotificationById_WhenNotFound_ShouldReturn404() throws Exception {
        when(service.getNotificationById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/notifications/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /notifications - Should send notification successfully")
    void sendNotification_ShouldReturnCreatedNotification() throws Exception {
        Notification newNotification = new Notification();
        newNotification.setUserId("user123");
        newNotification.setMessage("New notification message");
        newNotification.setSubject("Test Subject");

        Notification savedNotification = new Notification();
        savedNotification.setId("newNotif");
        savedNotification.setUserId("user123");
        savedNotification.setMessage("New notification message");
        savedNotification.setSubject("Test Subject");
        savedNotification.setStatus(Notification.NotificationStatus.SENT);

        when(service.sendNotification(any(Notification.class))).thenReturn(savedNotification);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newNotification)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("newNotif"))
                .andExpect(jsonPath("$.status").value("SENT"));

        verify(service, times(1)).sendNotification(any(Notification.class));
    }

    @Test
    @DisplayName("POST /notifications - Should return 400 for missing userId")
    void sendNotification_MissingUserId_ShouldReturn400() throws Exception {
        Notification invalidNotification = new Notification();
        invalidNotification.setMessage("Test message");
        // Missing userId

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidNotification)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /notifications - Should return 400 for missing message")
    void sendNotification_MissingMessage_ShouldReturn400() throws Exception {
        Notification invalidNotification = new Notification();
        invalidNotification.setUserId("user123");
        // Missing message

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidNotification)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /notifications/{id} - Should delete notification")
    void deleteNotification_ShouldReturn204() throws Exception {
        doNothing().when(service).deleteNotification("notif1");

        mockMvc.perform(delete("/notifications/notif1"))
                .andExpect(status().isNoContent());

        verify(service, times(1)).deleteNotification("notif1");
    }

    @Test
    @DisplayName("POST /notifications - Should handle failed notification status")
    void sendNotification_WhenFailed_ShouldReturnFailedStatus() throws Exception {
        Notification newNotification = new Notification();
        newNotification.setUserId("user123");
        newNotification.setMessage("Test message");

        Notification failedNotification = new Notification();
        failedNotification.setId("failedNotif");
        failedNotification.setUserId("user123");
        failedNotification.setMessage("Test message");
        failedNotification.setStatus(Notification.NotificationStatus.FAILED);

        when(service.sendNotification(any(Notification.class))).thenReturn(failedNotification);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newNotification)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /notifications/{id} - Should return notification with channels")
    void getNotificationById_ShouldReturnWithChannels() throws Exception {
        when(service.getNotificationById("notif1")).thenReturn(Optional.of(testNotification1));

        mockMvc.perform(get("/notifications/notif1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isArray())
                .andExpect(jsonPath("$.channels.length()").value(2));
    }
}
