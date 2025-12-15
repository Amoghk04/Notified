package com.notified.notification.service;

import com.notified.notification.client.UserPreferenceClient;
import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import com.notified.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private UserPreferenceClient preferenceClient;

    @Mock
    private NotificationChannelService channelService;

    @InjectMocks
    private NotificationService service;

    private Notification testNotification;
    private UserPreference testUserPreference;

    @BeforeEach
    void setUp() {
        // Set up test notification
        testNotification = new Notification();
        testNotification.setId("notif1");
        testNotification.setUserId("user123");
        testNotification.setMessage("Test notification message");
        testNotification.setSubject("Test Subject");
        testNotification.setStatus(Notification.NotificationStatus.PENDING);
        testNotification.setCreatedAt(LocalDateTime.now());

        // Set up test user preference
        testUserPreference = new UserPreference();
        testUserPreference.setUserId("user123");
        testUserPreference.setEmail("test@example.com");
        testUserPreference.setTelegramChatId("123456789");
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.EMAIL);
        channels.add(UserPreference.NotificationChannel.TELEGRAM);
        testUserPreference.setEnabledChannels(channels);
    }

    @Test
    @DisplayName("getAllNotifications - Should return all notifications")
    void getAllNotifications_ShouldReturnAllNotifications() {
        Notification notification2 = new Notification();
        notification2.setId("notif2");
        notification2.setUserId("user456");

        when(repository.findAll()).thenReturn(Arrays.asList(testNotification, notification2));

        List<Notification> result = service.getAllNotifications();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("notif1");
        assertThat(result.get(1).getId()).isEqualTo("notif2");
        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllNotifications - Should return empty list when no notifications")
    void getAllNotifications_ShouldReturnEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<Notification> result = service.getAllNotifications();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getNotificationsByUserId - Should return user notifications")
    void getNotificationsByUserId_ShouldReturnUserNotifications() {
        Notification notification2 = new Notification();
        notification2.setId("notif2");
        notification2.setUserId("user123");

        when(repository.findByUserId("user123")).thenReturn(Arrays.asList(testNotification, notification2));

        List<Notification> result = service.getNotificationsByUserId("user123");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getUserId().equals("user123"));
        verify(repository, times(1)).findByUserId("user123");
    }

    @Test
    @DisplayName("getNotificationById - Should return notification when found")
    void getNotificationById_WhenFound_ShouldReturnNotification() {
        when(repository.findById("notif1")).thenReturn(Optional.of(testNotification));

        Optional<Notification> result = service.getNotificationById("notif1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("notif1");
        assertThat(result.get().getUserId()).isEqualTo("user123");
    }

    @Test
    @DisplayName("getNotificationById - Should return empty when not found")
    void getNotificationById_WhenNotFound_ShouldReturnEmpty() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Notification> result = service.getNotificationById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sendNotification - Should send via enabled channels and return SENT status")
    void sendNotification_WithEnabledChannels_ShouldSendAndReturnSent() {
        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock channel service methods
        doNothing().when(channelService).sendEmailNotification(any(), any());
        doNothing().when(channelService).sendTelegramNotification(any(), any());

        Notification result = service.sendNotification(testNotification);

        assertThat(result.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        verify(channelService, times(1)).sendEmailNotification(testUserPreference, testNotification);
        verify(channelService, times(1)).sendTelegramNotification(testUserPreference, testNotification);
        verify(channelService, never()).sendWhatsappNotification(any(), any());
        verify(repository, times(1)).save(testNotification);
    }

    @Test
    @DisplayName("sendNotification - Should handle channel failure gracefully")
    void sendNotification_WhenChannelFails_ShouldContinueWithOtherChannels() {
        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Email fails but Telegram succeeds
        doThrow(new RuntimeException("Email service unavailable"))
            .when(channelService).sendEmailNotification(any(), any());
        doNothing().when(channelService).sendTelegramNotification(any(), any());

        Notification result = service.sendNotification(testNotification);

        // Should still be SENT if at least one channel worked
        assertThat(result.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);
        verify(channelService, times(1)).sendEmailNotification(any(), any());
        verify(channelService, times(1)).sendTelegramNotification(any(), any());
    }

    @Test
    @DisplayName("sendNotification - Should set FAILED status when preference lookup fails")
    void sendNotification_WhenPreferenceLookupFails_ShouldReturnFailed() {
        when(preferenceClient.getUserPreference("user123")).thenThrow(new RuntimeException("Service unavailable"));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.sendNotification(testNotification);

        assertThat(result.getStatus()).isEqualTo(Notification.NotificationStatus.FAILED);
        verify(channelService, never()).sendEmailNotification(any(), any());
        verify(channelService, never()).sendTelegramNotification(any(), any());
    }

    @Test
    @DisplayName("sendNotification - Should only send via enabled channels")
    void sendNotification_ShouldOnlySendViaEnabledChannels() {
        // Only Telegram enabled
        Set<UserPreference.NotificationChannel> telegramOnly = new HashSet<>();
        telegramOnly.add(UserPreference.NotificationChannel.TELEGRAM);
        testUserPreference.setEnabledChannels(telegramOnly);

        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(channelService).sendTelegramNotification(any(), any());

        Notification result = service.sendNotification(testNotification);

        assertThat(result.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);
        verify(channelService, never()).sendEmailNotification(any(), any());
        verify(channelService, times(1)).sendTelegramNotification(any(), any());
        verify(channelService, never()).sendWhatsappNotification(any(), any());
        verify(channelService, never()).sendAppNotification(any(), any());
    }

    @Test
    @DisplayName("sendNotification - Should send via WhatsApp when enabled")
    void sendNotification_ShouldSendViaWhatsAppWhenEnabled() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.WHATSAPP);
        testUserPreference.setEnabledChannels(channels);

        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(channelService).sendWhatsappNotification(any(), any());

        service.sendNotification(testNotification);

        verify(channelService, times(1)).sendWhatsappNotification(testUserPreference, testNotification);
    }

    @Test
    @DisplayName("sendNotification - Should send via App when enabled")
    void sendNotification_ShouldSendViaAppWhenEnabled() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.APP);
        testUserPreference.setEnabledChannels(channels);

        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(channelService).sendAppNotification(any(), any());

        service.sendNotification(testNotification);

        verify(channelService, times(1)).sendAppNotification(testUserPreference, testNotification);
    }

    @Test
    @DisplayName("sendNotification - Should populate channels from user preferences")
    void sendNotification_ShouldPopulateChannelsFromPreferences() {
        when(preferenceClient.getUserPreference("user123")).thenReturn(testUserPreference);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(channelService).sendEmailNotification(any(), any());
        doNothing().when(channelService).sendTelegramNotification(any(), any());

        Notification result = service.sendNotification(testNotification);

        assertThat(result.getChannels()).isNotNull();
        assertThat(result.getChannels()).hasSize(2);
    }

    @Test
    @DisplayName("deleteNotification - Should delete notification by ID")
    void deleteNotification_ShouldDeleteById() {
        doNothing().when(repository).deleteById("notif1");

        service.deleteNotification("notif1");

        verify(repository, times(1)).deleteById("notif1");
    }
}
