package com.x.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record CreateBusinessRequest(
        @NotNull(message = "Owner user ID is required")
        @Positive(message = "Owner user ID must be positive") Long ownerUserId,
        @NotBlank(message = "Business name is required")
        @Size(max = 160, message = "Business name must not exceed 160 characters") String name,
        @NotBlank(message = "Business code is required")
        @Size(max = 64, message = "Business code must not exceed 64 characters") String code,
        @NotBlank(message = "Default currency code is required")
        @Pattern(regexp = "[A-Za-z]{3}", message = "Default currency code must contain 3 letters") String defaultCurrencyCode,
        @Size(max = 100, message = "Tax registration number must not exceed 100 characters") String taxRegistrationNumber,
        @Size(max = 32, message = "Tax registration label must not exceed 32 characters") String taxRegistrationLabel,
        @Positive(message = "Default tax ID must be positive") Long defaultTaxId,
        Boolean pricesIncludeTax,
        @NotBlank(message = "Time zone is required")
        @Size(max = 64, message = "Time zone must not exceed 64 characters") String timeZone,
        @NotNull(message = "Fiscal year start month is required")
        @Min(value = 1, message = "Fiscal year start month must be between 1 and 12")
        @Max(value = 12, message = "Fiscal year start month must be between 1 and 12") Integer fiscalYearStartMonth) {
}
