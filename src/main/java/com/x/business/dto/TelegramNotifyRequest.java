package com.x.business.dto;

import com.x.business.telegram.TelegramAlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An alert from another service; it is sent only if the business wants this kind of alert. */
public record TelegramNotifyRequest(
        @NotNull TelegramAlertType type,
        @NotBlank @Size(max = 3500) String text) {
}
