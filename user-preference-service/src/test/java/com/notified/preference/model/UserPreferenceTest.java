package com.notified.preference.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserPreference Model Tests")
class UserPreferenceTest {

    private UserPreference userPreference;

    @BeforeEach
    void setUp() {
        userPreference = new UserPreference();
    }

    @Test
    @DisplayName("Default constructor should initialize empty collections")
    void defaultConstructor_ShouldInitializeEmptyCollections() {
        assertThat(userPreference.getPreferences()).isNotNull().isEmpty();
        assertThat(userPreference.getEnabledChannels()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Parameterized constructor should set values correctly")
    void parameterizedConstructor_ShouldSetValues() {
        UserPreference user = new UserPreference("user1", "email@test.com", "1234567890", "SPORTS");

        assertThat(user.getUserId()).isEqualTo("user1");
        assertThat(user.getEmail()).isEqualTo("email@test.com");
        assertThat(user.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(user.getPreference()).isEqualTo("SPORTS");
    }

    @Test
    @DisplayName("isEmailEnabled should return true when EMAIL channel is enabled")
    void isEmailEnabled_WhenEnabled_ShouldReturnTrue() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.EMAIL);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isEmailEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEmailEnabled should return false when EMAIL channel is not enabled")
    void isEmailEnabled_WhenDisabled_ShouldReturnFalse() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.TELEGRAM);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEmailEnabled should return false when channels is null")
    void isEmailEnabled_WhenNull_ShouldReturnFalse() {
        userPreference.setEnabledChannels(null);
        assertThat(userPreference.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isTelegramEnabled should return correct value")
    void isTelegramEnabled_ShouldReturnCorrectValue() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.TELEGRAM);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isTelegramEnabled()).isTrue();
        assertThat(userPreference.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isWhatsappEnabled should return correct value")
    void isWhatsappEnabled_ShouldReturnCorrectValue() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.WHATSAPP);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isWhatsappEnabled()).isTrue();
    }

    @Test
    @DisplayName("isAppEnabled should return correct value")
    void isAppEnabled_ShouldReturnCorrectValue() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.APP);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isAppEnabled()).isTrue();
    }

    @Test
    @DisplayName("Multiple channels can be enabled")
    void multipleChannels_CanBeEnabled() {
        Set<UserPreference.NotificationChannel> channels = new HashSet<>();
        channels.add(UserPreference.NotificationChannel.EMAIL);
        channels.add(UserPreference.NotificationChannel.TELEGRAM);
        channels.add(UserPreference.NotificationChannel.APP);
        userPreference.setEnabledChannels(channels);

        assertThat(userPreference.isEmailEnabled()).isTrue();
        assertThat(userPreference.isTelegramEnabled()).isTrue();
        assertThat(userPreference.isAppEnabled()).isTrue();
        assertThat(userPreference.isWhatsappEnabled()).isFalse();
    }

    @Test
    @DisplayName("getNotificationIntervalMinutes should return default when null")
    void getNotificationIntervalMinutes_WhenNull_ShouldReturnDefault() {
        userPreference.setNotificationIntervalMinutes(null);
        assertThat(userPreference.getNotificationIntervalMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("getNotificationIntervalMinutes should return set value")
    void getNotificationIntervalMinutes_WhenSet_ShouldReturnValue() {
        userPreference.setNotificationIntervalMinutes(120);
        assertThat(userPreference.getNotificationIntervalMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("Preferences list should be modifiable")
    void preferencesList_ShouldBeModifiable() {
        userPreference.setPreferences(Arrays.asList("SPORTS", "NEWS"));
        
        assertThat(userPreference.getPreferences()).hasSize(2);
        assertThat(userPreference.getPreferences()).contains("SPORTS", "NEWS");
    }

    @Test
    @DisplayName("LastNotificationSent should be settable")
    void lastNotificationSent_ShouldBeSettable() {
        LocalDateTime now = LocalDateTime.now();
        userPreference.setLastNotificationSent(now);

        assertThat(userPreference.getLastNotificationSent()).isEqualTo(now);
    }

    @Test
    @DisplayName("All setters and getters should work correctly")
    void allSettersAndGetters_ShouldWork() {
        userPreference.setId("id123");
        userPreference.setUserId("user123");
        userPreference.setEmail("test@test.com");
        userPreference.setPhoneNumber("+1234567890");
        userPreference.setTelegramChatId("987654321");
        userPreference.setTelegramUsername("@testuser");
        userPreference.setPreference("SPORTS");

        assertThat(userPreference.getId()).isEqualTo("id123");
        assertThat(userPreference.getUserId()).isEqualTo("user123");
        assertThat(userPreference.getEmail()).isEqualTo("test@test.com");
        assertThat(userPreference.getPhoneNumber()).isEqualTo("+1234567890");
        assertThat(userPreference.getTelegramChatId()).isEqualTo("987654321");
        assertThat(userPreference.getTelegramUsername()).isEqualTo("@testuser");
        assertThat(userPreference.getPreference()).isEqualTo("SPORTS");
    }

    @Test
    @DisplayName("NotificationChannel enum should have all expected values")
    void notificationChannel_ShouldHaveAllValues() {
        UserPreference.NotificationChannel[] channels = UserPreference.NotificationChannel.values();
        
        assertThat(channels).hasSize(5);
        assertThat(channels).contains(
            UserPreference.NotificationChannel.EMAIL,
            UserPreference.NotificationChannel.WHATSAPP,
            UserPreference.NotificationChannel.APP,
            UserPreference.NotificationChannel.SMS,
            UserPreference.NotificationChannel.TELEGRAM
        );
    }
}
