package com.x.business.dto;

/** Whether the business's Telegram chat is connected, and which alerts it receives. */
public record TelegramStatusResponse(
        boolean botConfigured,
        String botUsername,
        boolean connected,
        String chatTitle,
        boolean khqrAlerts,
        boolean orderAlerts,
        boolean lowStockAlerts,
        boolean dailySummary,
        int lowStockThreshold,
        int dailySummaryHour) {
}
