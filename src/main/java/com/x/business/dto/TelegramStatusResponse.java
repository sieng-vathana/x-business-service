package com.x.business.dto;

import java.util.List;

/**
 * The business's Telegram chats and their alerts. connected and the alert switches summarise all chats:
 * true when at least one chat is connected or wants that alert.
 */
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
        int dailySummaryHour,
        /** A connect link is open and nobody has pressed Start with it yet. */
        boolean linkPending,
        List<TelegramChatResponse> chats) {
}
