package com.notified.notification.service;

import com.notified.notification.model.Notification;
import com.notified.notification.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationChannelService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationChannelService.class);

    private final JavaMailSender mailSender;

    public NotificationChannelService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmailNotification(UserPreference preference, Notification notification) {
        if (preference.getEmail() != null && !preference.getEmail().isEmpty()) {
            try {
                if (mailSender != null) {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(preference.getEmail());
                    message.setSubject(notification.getSubject() != null ? notification.getSubject() : "Notification");
                    message.setText(notification.getMessage());
                    mailSender.send(message);
                    logger.info("Email sent to: {}", preference.getEmail());
                } else {
                    logger.info("Email would be sent to: {} (Mail sender not configured)", preference.getEmail());
                }
            } catch (Exception e) {
                logger.error("Failed to send email to: {}", preference.getEmail(), e);
            }
        }
    }

    public void sendSmsNotification(UserPreference preference, Notification notification) {
        if (preference.getPhoneNumber() != null && !preference.getPhoneNumber().isEmpty()) {
            // SMS integration would go here (e.g., Twilio, AWS SNS)
            logger.info("SMS would be sent to: {} with message: {}", 
                preference.getPhoneNumber(), notification.getMessage());
        }
    }

    public void sendAppNotification(UserPreference preference, Notification notification) {
        // Push notification integration would go here (e.g., Firebase Cloud Messaging)
        logger.info("App notification would be sent to user: {} with message: {}", 
            preference.getUserId(), notification.getMessage());
    }
}
