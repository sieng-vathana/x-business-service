package com.x.business.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * The platform's Telegram bot for merchant alerts. The token comes from the TELEGRAM_BOT_TOKEN
 * environment variable; while it is empty, Telegram alerts are switched off.
 */
@ConfigurationProperties(prefix = "x.telegram")
public class TelegramProperties {
    private String botToken;
    private String apiBaseUrl = "https://api.telegram.org";
    /** Reads /start messages with long polling. Telegram allows only one poller per bot. */
    private boolean pollingEnabled = true;

    public boolean isConfigured() {
        return StringUtils.hasText(botToken);
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public boolean isPollingEnabled() {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }
}
