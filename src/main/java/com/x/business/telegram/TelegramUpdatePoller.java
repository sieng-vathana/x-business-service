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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final Map<String, String> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("here", "Send TinhLok alerts to this topic");
        COMMANDS.put("stop", "Turn off TinhLok alerts for this chat");
    }

    private long offset;
    private Instant pausedUntil = Instant.EPOCH;
    private boolean commandsRegistered;

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void poll() {
        if (!properties.isConfigured() || !properties.isPollingEnabled() || Instant.now().isBefore(pausedUntil)) {
            return;
        }
        registerCommands();
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
        // In a group with topics, a command from a topic carries its thread; the General topic has none.
        Long threadId = message.path("is_topic_message").asBoolean(false) ? message.path("message_thread_id").asLong() : null;
        String[] parts = text.split("\\s+", 2);
        // Groups send commands as /start@BotName.
        String command = parts[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        if ("/start".equals(command)) {
            telegramService.deliver(chatId, threadId, parts.length > 1
                    ? connect(parts[1].trim(), chatId, threadId, chatTitle(message.path("chat")))
                    : HOW_TO_CONNECT);
        } else if ("/here".equals(command)) {
            List<String> businesses = telegramService.useTopic(chatId, threadId);
            telegramService.deliver(chatId, threadId, businesses.isEmpty()
                    ? HOW_TO_CONNECT
                    : "✅ TinhLok alerts for " + String.join(", ", businesses) + " will arrive "
                            + (threadId == null ? "in this chat." : "in this topic."));
        } else if ("/stop".equals(command)) {
            telegramService.deliver(chatId, threadId, telegramService.forgetChat(chatId) > 0
                    ? "TinhLok alerts are turned off for this chat."
                    : "This chat does not receive TinhLok alerts.");
        }
    }

    private String connect(String code, long chatId, Long threadId, String chatTitle) {
        return telegramService.linkChat(code, chatId, threadId, chatTitle)
                .map(businessName -> "✅ TinhLok alerts are on for " + businessName + ".\n"
                        + "This chat will get KHQR payments, online orders, low stock and a daily summary. "
                        + "Choose them in Settings → Telegram alerts. In a group with topics, send /here in the topic "
                        + "that should get them. Send /stop to turn them off.")
                .orElse("This link has expired. In TinhLok, open Settings → Telegram alerts and tap Connect Telegram again.");
    }

    /** Shows the bot's commands in Telegram's menu; tried until it works once. */
    private void registerCommands() {
        if (commandsRegistered) {
            return;
        }
        try {
            telegramClient.setCommands(COMMANDS);
            commandsRegistered = true;
        } catch (TelegramClient.TelegramException exception) {
            log.warn("Telegram bot commands could not be registered: {}", exception.getMessage());
        }
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
