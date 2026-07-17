package com.VyntraBusinessService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(
        @NotNull(message = "Owner user ID is required")
        @Positive(message = "Owner user ID must be positive") Long ownerUserId,
        @NotBlank(message = "Business name is required")
        @Size(max = 160, message = "Business name must not exceed 160 characters") String name,
        @NotBlank(message = "Business code is required")
        @Size(max = 64, message = "Business code must not exceed 64 characters") String code) {
}
