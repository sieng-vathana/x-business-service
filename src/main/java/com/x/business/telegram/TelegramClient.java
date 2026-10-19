package com.x.business.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.business.config.TelegramProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Calls the Telegram Bot API. Error messages never contain the bot token. */
@Component
public class TelegramClient {

    private final TelegramProperties properties;
    private final RestClient client;
    private volatile String botUsername;

    public TelegramClient(RestClient.Builder builder, TelegramProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // Long polling keeps getUpdates open for up to TelegramUpdatePoller.LONG_POLL_SECONDS.
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.client = builder.clone().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** The bot's @username for t.me links, or null when Telegram cannot be reached. */
    public String botUsername() {
        if (botUsername == null && isConfigured()) {
            try {
                botUsername = call("getMe", Map.of()).path("username").asText(null);
            } catch (TelegramException exception) {
                return null;
            }
        }
        return botUsername;
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    /** Sends to a topic of a group with topics when threadId is set, otherwise to the chat itself. */
    public void sendMessage(long chatId, Long threadId, String text) {
        sendMessage(chatId, threadId, text, null);
    }

    /** Same as {@link #sendMessage(long, Long, String)}, with Telegram formatting such as "HTML" when parseMode is set. */
    public void sendMessage(long chatId, Long threadId, String text, String parseMode) {
        Map<String, Object> body = new HashMap<>(Map.of("chat_id", chatId, "text", text, "disable_web_page_preview", true));
        if (threadId != null) {
            body.put("message_thread_id", threadId);
        }
        if (parseMode != null) {
            body.put("parse_mode", parseMode);
        }
        call("sendMessage", body);
    }

    /** Lists the bot's commands in Telegram's command menu, so /here can be picked inside a topic. */
    public void setCommands(Map<String, String> commands) {
        call("setMyCommands", Map.of("commands", commands.entrySet().stream()
                .map(command -> Map.of("command", command.getKey(), "description", command.getValue()))
                .toList()));
    }

    /** Waits up to timeoutSeconds for messages and chat membership changes after offset. */
    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        return call("getUpdates", Map.of(
                "offset", offset,
                "timeout", timeoutSeconds,
                "allowed_updates", List.of("message", "my_chat_member")));
    }

    private JsonNode call(String method, Map<String, ?> body) {
        if (!isConfigured()) {
            throw new TelegramException("The Telegram bot token is not configured", 0);
        }
        JsonNode response;
        try {
            response = client.post()
                    .uri(URI.create(properties.getApiBaseUrl() + "/bot" + properties.getBotToken() + "/" + method))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    // Telegram explains failures in the JSON body, so it is read for every status.
                    .onStatus(status -> true, (request, errorResponse) -> { })
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new TelegramException(redact(exception.getMessage()), 0);
        }
        if (response == null) {
            throw new TelegramException("Telegram returned no response", 0);
        }
        if (!response.path("ok").asBoolean(false)) {
            throw new TelegramException(
                    redact(response.path("description").asText("Telegram request failed")),
                    response.path("error_code").asInt(0));
        }
        return response.path("result");
    }

    private String redact(String message) {
        return message == null ? null : message.replace(properties.getBotToken(), "<token>");
    }

    public static class TelegramException extends RuntimeException {
        private final int errorCode;

        public TelegramException(String message, int errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }

        /** The chat no longer takes messages from the bot: it blocked or removed the bot, or was deleted. */
        public boolean chatGone() {
            String message = getMessage() == null ? "" : getMessage().toLowerCase(Locale.ROOT);
            return errorCode == 403 || (errorCode == 400 && message.contains("chat not found"));
        }

        /** The group has topics and the one the bot writes to is closed, or no longer exists. */
        public boolean topicUnavailable() {
            String message = getMessage() == null ? "" : getMessage().toUpperCase(Locale.ROOT);
            return message.contains("TOPIC_CLOSED") || message.contains("THREAD NOT FOUND") || message.contains("TOPIC_DELETED");
        }
    }
}
