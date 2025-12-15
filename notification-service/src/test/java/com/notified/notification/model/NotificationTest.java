package com.notified.notification.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Model Tests")
class NotificationTest {

    private Notification notification;

    @BeforeEach
    void setUp() {
        notification = new Notification();
    }

    @Test
    @DisplayName("Default constructor should initialize with PENDING status")
    void defaultConstructor_ShouldInitializeWithPendingStatus() {
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("Default constructor should set createdAt")
    void defaultConstructor_ShouldSetCreatedAt() {
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("Parameterized constructor should set values correctly")
    void parameterizedConstructor_ShouldSetValues() {
        Notification notif = new Notification("user123", "Test message", "Test Subject");

        assertThat(notif.getUserId()).isEqualTo("user123");
        assertThat(notif.getMessage()).isEqualTo("Test message");
        assertThat(notif.getSubject()).isEqualTo("Test Subject");
        assertThat(notif.getStatus()).isEqualTo(Notification.NotificationStatus.PENDING);
        assertThat(notif.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("All setters and getters should work correctly")
    void allSettersAndGetters_ShouldWork() {
        LocalDateTime now = LocalDateTime.now();
        Set<Notification.NotificationChannel> channels = new HashSet<>();
        channels.add(Notification.NotificationChannel.EMAIL);
        channels.add(Notification.NotificationChannel.TELEGRAM);

        notification.setId("id123");
        notification.setUserId("user456");
        notification.setMessage("Test notification message");
        notification.setSubject("Test Subject");
        notification.setChannels(channels);
        notification.setStatus(Notification.NotificationStatus.SENT);
        notification.setCreatedAt(now.minusHours(1));
        notification.setSentAt(now);
        notification.setArticleContentHash("hash123");
        notification.setTelegramMessageId(12345L);
        notification.setUserReaction("like");

        assertThat(notification.getId()).isEqualTo("id123");
        assertThat(notification.getUserId()).isEqualTo("user456");
        assertThat(notification.getMessage()).isEqualTo("Test notification message");
        assertThat(notification.getSubject()).isEqualTo("Test Subject");
        assertThat(notification.getChannels()).containsExactlyInAnyOrder(
            Notification.NotificationChannel.EMAIL,
            Notification.NotificationChannel.TELEGRAM
        );
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);
        assertThat(notification.getCreatedAt()).isEqualTo(now.minusHours(1));
        assertThat(notification.getSentAt()).isEqualTo(now);
        assertThat(notification.getArticleContentHash()).isEqualTo("hash123");
        assertThat(notification.getTelegramMessageId()).isEqualTo(12345L);
        assertThat(notification.getUserReaction()).isEqualTo("like");
    }

    @Test
    @DisplayName("NotificationChannel enum should have all expected values")
    void notificationChannel_ShouldHaveAllValues() {
        Notification.NotificationChannel[] channels = Notification.NotificationChannel.values();

        assertThat(channels).hasSize(5);
        assertThat(channels).contains(
            Notification.NotificationChannel.EMAIL,
            Notification.NotificationChannel.WHATSAPP,
            Notification.NotificationChannel.APP,
            Notification.NotificationChannel.SMS,
            Notification.NotificationChannel.TELEGRAM
        );
    }

    @Test
    @DisplayName("NotificationStatus enum should have all expected values")
    void notificationStatus_ShouldHaveAllValues() {
        Notification.NotificationStatus[] statuses = Notification.NotificationStatus.values();

        assertThat(statuses).hasSize(3);
        assertThat(statuses).contains(
            Notification.NotificationStatus.PENDING,
            Notification.NotificationStatus.SENT,
            Notification.NotificationStatus.FAILED
        );
    }

    @Test
    @DisplayName("Channels set should be modifiable")
    void channelsSet_ShouldBeModifiable() {
        Set<Notification.NotificationChannel> channels = new HashSet<>();
        channels.add(Notification.NotificationChannel.EMAIL);
        notification.setChannels(channels);

        assertThat(notification.getChannels()).hasSize(1);

        channels.add(Notification.NotificationChannel.TELEGRAM);
        notification.setChannels(channels);

        assertThat(notification.getChannels()).hasSize(2);
    }

    @Test
    @DisplayName("Status transitions should work correctly")
    void statusTransitions_ShouldWork() {
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.PENDING);

        notification.setStatus(Notification.NotificationStatus.SENT);
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);

        notification.setStatus(Notification.NotificationStatus.FAILED);
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("UserReaction can be null, like, or dislike")
    void userReaction_ShouldAcceptValidValues() {
        assertThat(notification.getUserReaction()).isNull();

        notification.setUserReaction("like");
        assertThat(notification.getUserReaction()).isEqualTo("like");

        notification.setUserReaction("dislike");
        assertThat(notification.getUserReaction()).isEqualTo("dislike");

        notification.setUserReaction(null);
        assertThat(notification.getUserReaction()).isNull();
    }

    @Test
    @DisplayName("TelegramMessageId can be null or a valid Long")
    void telegramMessageId_ShouldAcceptValidValues() {
        assertThat(notification.getTelegramMessageId()).isNull();

        notification.setTelegramMessageId(12345L);
        assertThat(notification.getTelegramMessageId()).isEqualTo(12345L);

        notification.setTelegramMessageId(null);
        assertThat(notification.getTelegramMessageId()).isNull();
    }

    @Test
    @DisplayName("ArticleContentHash should be settable for deduplication")
    void articleContentHash_ShouldBeSettable() {
        assertThat(notification.getArticleContentHash()).isNull();

        notification.setArticleContentHash("abc123hash");
        assertThat(notification.getArticleContentHash()).isEqualTo("abc123hash");
    }

    @Test
    @DisplayName("SentAt should be null for unsent notifications")
    void sentAt_ShouldBeNullForUnsent() {
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("SentAt should be set when notification is sent")
    void sentAt_ShouldBeSetWhenSent() {
        LocalDateTime sentTime = LocalDateTime.now();
        notification.setSentAt(sentTime);
        notification.setStatus(Notification.NotificationStatus.SENT);

        assertThat(notification.getSentAt()).isEqualTo(sentTime);
        assertThat(notification.getStatus()).isEqualTo(Notification.NotificationStatus.SENT);
    }
}
