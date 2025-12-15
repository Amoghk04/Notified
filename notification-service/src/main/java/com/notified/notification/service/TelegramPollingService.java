package com.notified.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@EnableAsync
public class TelegramPollingService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramPollingService.class);

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.polling.enabled:true}")
    private boolean pollingEnabled;

    private final TelegramBotService telegramBotService;
    private final RestTemplate restTemplate;
    
    private volatile boolean running = false;
    private long lastUpdateId = 0;

    public TelegramPollingService(TelegramBotService telegramBotService, RestTemplate restTemplate) {
        this.telegramBotService = telegramBotService;
        this.restTemplate = restTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (pollingEnabled && botToken != null && !botToken.isEmpty()) {
            startPolling();
        } else {
            logger.info("Telegram polling is disabled or bot token not configured");
        }
    }

    @Async
    public void startPolling() {
        if (running) {
            logger.warn("Telegram polling is already running");
            return;
        }
        
        running = true;
        logger.info("Starting Telegram long polling...");
        
        while (running) {
            try {
                pollUpdates();
            } catch (Exception e) {
                logger.error("Error during Telegram polling: {}", e.getMessage());
                try {
                    Thread.sleep(5000); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Telegram polling stopped");
    }

    public void stopPolling() {
        running = false;
    }

    @SuppressWarnings("unchecked")
    private void pollUpdates() {
        String url = String.format(
            "https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=30",
            botToken, lastUpdateId + 1
        );

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("result");
                
                if (updates != null && !updates.isEmpty()) {
                    for (Map<String, Object> update : updates) {
                        try {
                            // Get update_id
                            Object updateIdObj = update.get("update_id");
                            if (updateIdObj != null) {
                                long updateId = ((Number) updateIdObj).longValue();
                                lastUpdateId = Math.max(lastUpdateId, updateId);
                            }
                            
                            // Process the update
                            logger.info("Processing Telegram update: {}", update);
                            telegramBotService.processUpdate(update);
                            
                        } catch (Exception e) {
                            logger.error("Error processing update: {}", e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get updates from Telegram: {}", e.getMessage());
            throw e;
        }
    }
}
