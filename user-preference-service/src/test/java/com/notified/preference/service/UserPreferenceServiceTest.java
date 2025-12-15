package com.notified.preference.service;

import com.notified.preference.model.UserPreference;
import com.notified.preference.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceService Tests")
class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository repository;

    @InjectMocks
    private UserPreferenceService service;

    private UserPreference testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPreference();
        testUser.setId("1");
        testUser.setUserId("testUser123");
        testUser.setEmail("test@example.com");
        testUser.setTelegramChatId("123456789");
        testUser.setPreferences(Arrays.asList("SPORTS", "TECHNOLOGY"));
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.EMAIL);
        channels.add(UserPreference.NotificationChannel.TELEGRAM);
        testUser.setEnabledChannels(channels);
        testUser.setNotificationIntervalMinutes(60);
    }

    @Test
    @DisplayName("getAllPreferences - Should return all preferences")
    void getAllPreferences_ShouldReturnAllUsers() {
        UserPreference user2 = new UserPreference();
        user2.setUserId("user2");
        user2.setEmail("user2@example.com");
        
        when(repository.findAll()).thenReturn(Arrays.asList(testUser, user2));

        List<UserPreference> result = service.getAllPreferences();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo("testUser123");
        assertThat(result.get(1).getUserId()).isEqualTo("user2");
        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllPreferences - Should return empty list when no users")
    void getAllPreferences_ShouldReturnEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<UserPreference> result = service.getAllPreferences();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPreferenceByUserId - Should return user when found")
    void getPreferenceByUserId_WhenFound_ShouldReturnUser() {
        when(repository.findByUserId("testUser123")).thenReturn(Optional.of(testUser));

        Optional<UserPreference> result = service.getPreferenceByUserId("testUser123");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("testUser123");
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().isEmailEnabled()).isTrue();
        assertThat(result.get().isTelegramEnabled()).isTrue();
    }

    @Test
    @DisplayName("getPreferenceByUserId - Should return empty when not found")
    void getPreferenceByUserId_WhenNotFound_ShouldReturnEmpty() {
        when(repository.findByUserId("nonexistent")).thenReturn(Optional.empty());

        Optional<UserPreference> result = service.getPreferenceByUserId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createPreference - Should save and return new preference")
    void createPreference_ShouldSaveAndReturnUser() {
        when(repository.save(any(UserPreference.class))).thenReturn(testUser);

        UserPreference result = service.createPreference(testUser);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("testUser123");
        assertThat(result.getPreferences()).contains("SPORTS", "TECHNOLOGY");
        verify(repository, times(1)).save(any(UserPreference.class));
    }

    @Test
    @DisplayName("updatePreference - Should update existing user")
    void updatePreference_WhenExists_ShouldUpdateUser() {
        UserPreference updatedPref = new UserPreference();
        updatedPref.setEmail("updated@example.com");
        updatedPref.setTelegramChatId("999999999");
        updatedPref.setPreferences(Arrays.asList("NEWS", "FINANCE"));
        updatedPref.setNotificationIntervalMinutes(120);
        LocalDateTime lastSent = LocalDateTime.now();
        updatedPref.setLastNotificationSent(lastSent);

        when(repository.findByUserId("testUser123")).thenReturn(Optional.of(testUser));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreference result = service.updatePreference("testUser123", updatedPref);

        assertThat(result.getEmail()).isEqualTo("updated@example.com");
        assertThat(result.getTelegramChatId()).isEqualTo("999999999");
        assertThat(result.getPreferences()).contains("NEWS", "FINANCE");
        assertThat(result.getNotificationIntervalMinutes()).isEqualTo(120);
        assertThat(result.getLastNotificationSent()).isEqualTo(lastSent);
        verify(repository, times(1)).save(any(UserPreference.class));
    }

    @Test
    @DisplayName("updatePreference - Should create new user when not exists")
    void updatePreference_WhenNotExists_ShouldCreateUser() {
        UserPreference newPref = new UserPreference();
        newPref.setEmail("new@example.com");
        newPref.setPreferences(Arrays.asList("SPORTS"));

        when(repository.findByUserId("newUser")).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> {
            UserPreference saved = inv.getArgument(0);
            saved.setId("generatedId");
            return saved;
        });

        UserPreference result = service.updatePreference("newUser", newPref);

        assertThat(result.getUserId()).isEqualTo("newUser");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(repository, times(1)).save(any(UserPreference.class));
    }

    @Test
    @DisplayName("deletePreference - Should delete when user exists")
    void deletePreference_WhenExists_ShouldDelete() {
        when(repository.findByUserId("testUser123")).thenReturn(Optional.of(testUser));
        doNothing().when(repository).delete(any(UserPreference.class));

        service.deletePreference("testUser123");

        verify(repository, times(1)).delete(testUser);
    }

    @Test
    @DisplayName("deletePreference - Should do nothing when user not exists")
    void deletePreference_WhenNotExists_ShouldDoNothing() {
        when(repository.findByUserId("nonexistent")).thenReturn(Optional.empty());

        service.deletePreference("nonexistent");

        verify(repository, never()).delete(any(UserPreference.class));
    }

    @Test
    @DisplayName("Should correctly identify enabled channels")
    void shouldCorrectlyIdentifyEnabledChannels() {
        assertThat(testUser.isEmailEnabled()).isTrue();
        assertThat(testUser.isTelegramEnabled()).isTrue();
        assertThat(testUser.isWhatsappEnabled()).isFalse();
        assertThat(testUser.isAppEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should return default notification interval when null")
    void shouldReturnDefaultIntervalWhenNull() {
        UserPreference userWithNullInterval = new UserPreference();
        userWithNullInterval.setUserId("test");
        userWithNullInterval.setNotificationIntervalMinutes(null);

        assertThat(userWithNullInterval.getNotificationIntervalMinutes()).isEqualTo(60);
    }
}
