package com.x.business.dto;

import com.x.business.telegram.TelegramAlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * An alert from another service; it goes to the business's chats that want this kind of alert
 * and cover the store. Without a store it goes to every such chat. parseMode "HTML" sends it formatted.
 */
public record TelegramNotifyRequest(
        @NotNull TelegramAlertType type,
        @Positive Long storeId,
        @NotBlank @Size(max = 3500) String text,
        @Pattern(regexp = "HTML") String parseMode) {
}
