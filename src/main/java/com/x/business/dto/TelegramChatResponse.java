package com.x.business.dto;

import java.time.LocalDateTime;
import java.util.List;

/** One connected chat or topic: where it is, which stores it covers and which alerts it gets. */
public record TelegramChatResponse(
        Long id,
        String chatTitle,
        String chatType,
        String topicTitle,
        boolean inTopic,
        boolean allStores,
        List<Long> storeIds,
        boolean khqrAlerts,
        boolean orderAlerts,
        boolean lowStockAlerts,
        boolean dailySummary,
        LocalDateTime connectedAt) {
}
