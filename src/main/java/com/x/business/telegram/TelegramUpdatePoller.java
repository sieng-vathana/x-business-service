package com.x.business.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.business.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Reads the bot's updates with long polling: a /start with a link code connects that chat,
 * and /stop or removing the bot disconnects it. Telegram allows one poller per bot,
 * so only one instance of this service may run with polling enabled.
 */
@Component
@RequiredArgsConstructor
public class TelegramUpdatePoller {

    static final int LONG_POLL_SECONDS = 25;
    private static final Duration RETRY_AFTER_ERROR = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdatePoller.class);
    private static final String HOW_TO_CONNECT =
            "To get alerts here, open Settings → Telegram alerts in the TinhLok Merchant Portal and tap Connect Telegram.";

    private final TelegramProperties properties;
    private final TelegramClient telegramClient;
    private final TelegramService telegramService;
    private long offset;
    private Instant pausedUntil = Instant.EPOCH;

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void poll() {
        if (!properties.isConfigured() || !properties.isPollingEnabled() || Instant.now().isBefore(pausedUntil)) {
            return;
        }
        JsonNode updates;
        try {
            updates = telegramClient.getUpdates(offset, LONG_POLL_SECONDS);
        } catch (TelegramClient.TelegramException exception) {
            log.warn("Reading Telegram updates failed, retrying in {} s: {}", RETRY_AFTER_ERROR.toSeconds(), exception.getMessage());
            pausedUntil = Instant.now().plus(RETRY_AFTER_ERROR);
            return;
        }
        for (JsonNode update : updates) {
            offset = Math.max(offset, update.path("update_id").asLong() + 1);
            try {
                handle(update);
            } catch (RuntimeException exception) {
                log.warn("Telegram update {} could not be handled: {}", update.path("update_id").asLong(), exception.getMessage());
            }
        }
    }

    void handle(JsonNode update) {
        JsonNode membership = update.path("my_chat_member");
        if (!membership.isMissingNode()) {
            String status = membership.path("new_chat_member").path("status").asText("");
            if ("left".equals(status) || "kicked".equals(status)) {
                telegramService.forgetChat(membership.path("chat").path("id").asLong());
            }
            return;
        }
        JsonNode message = update.path("message");
        String text = message.path("text").asText("").trim();
        if (!text.startsWith("/")) {
            return;
        }
        long chatId = message.path("chat").path("id").asLong();
        String[] parts = text.split("\\s+", 2);
        // Groups send commands as /start@BotName.
        String command = parts[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        if ("/start".equals(command)) {
            telegramService.deliver(chatId, parts.length > 1
                    ? connect(parts[1].trim(), chatId, chatTitle(message.path("chat")))
                    : HOW_TO_CONNECT);
        } else if ("/stop".equals(command)) {
            telegramService.deliver(chatId, telegramService.forgetChat(chatId) > 0
                    ? "TinhLok alerts are turned off for this chat."
                    : "This chat does not receive TinhLok alerts.");
        }
    }

    private String connect(String code, long chatId, String chatTitle) {
        return telegramService.linkChat(code, chatId, chatTitle)
                .map(businessName -> "✅ TinhLok alerts are on for " + businessName + ".\n"
                        + "This chat will get KHQR payments, online orders, low stock and a daily summary. "
                        + "Choose them in Settings → Telegram alerts. Send /stop to turn them off.")
                .orElse("This link has expired. In TinhLok, open Settings → Telegram alerts and tap Connect Telegram again.");
    }

    private static String chatTitle(JsonNode chat) {
        if (chat.hasNonNull("title")) {
            return chat.path("title").asText();
        }
        String name = (chat.path("first_name").asText("") + " " + chat.path("last_name").asText("")).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return chat.hasNonNull("username") ? "@" + chat.path("username").asText() : null;
    }
}
