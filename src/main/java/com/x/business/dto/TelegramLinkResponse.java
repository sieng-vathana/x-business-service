package com.x.business.dto;

import java.time.LocalDateTime;

/** One-time t.me links that connect a private chat or a group to the business's alerts. */
public record TelegramLinkResponse(String privateChatUrl, String groupChatUrl, LocalDateTime expiresAt) {
}
