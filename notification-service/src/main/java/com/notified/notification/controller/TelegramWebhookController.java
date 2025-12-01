package com.notified.notification.controller;

import com.notified.notification.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramBotService telegramBotService;

    public TelegramWebhookController(TelegramBotService telegramBotService) {
        this.telegramBotService = telegramBotService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> update) {
        try {
            logger.info("Received Telegram update: {}", update);
            telegramBotService.processUpdate(update);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("Error processing Telegram webhook", e);
            return ResponseEntity.ok("OK"); // Always return 200 to Telegram
        }
    }

    @GetMapping("/webhook")
    public ResponseEntity<String> webhookInfo() {
        return ResponseEntity.ok("Telegram webhook endpoint is active");
    }
}
