package com.x.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateBusinessRequest(
        @NotBlank @Size(max = 160) String name,
        @Pattern(regexp = "[A-Za-z]{3}", message = "Default currency code must contain 3 letters") String defaultCurrencyCode,
        @Positive(message = "USD to KHR exchange rate must be positive") BigDecimal usdToKhrExchangeRate,
        Boolean pricesIncludeTax,
        @Size(max = 100, message = "Tax registration number must not exceed 100 characters") String taxRegistrationNumber,
        @Size(max = 32, message = "Tax registration label must not exceed 32 characters") String taxRegistrationLabel,
        /** Empty string clears the account; null leaves it unchanged. */
        @Size(max = 32, message = "Bakong account ID must not exceed 32 characters")
        @Pattern(regexp = "|[A-Za-z0-9._-]+@[A-Za-z0-9._-]+", message = "Bakong account ID must look like name@bank")
        String bakongAccountId) {

    public UpdateBusinessRequest(String name) {
        this(name, null, null, null, null, null, null);
    }

    public UpdateBusinessRequest(String name, String defaultCurrencyCode, BigDecimal usdToKhrExchangeRate,
                                 Boolean pricesIncludeTax, String taxRegistrationNumber, String taxRegistrationLabel) {
        this(name, defaultCurrencyCode, usdToKhrExchangeRate, pricesIncludeTax,
                taxRegistrationNumber, taxRegistrationLabel, null);
    }

    public UpdateBusinessRequest(String name, String defaultCurrencyCode, BigDecimal usdToKhrExchangeRate,
                                 Boolean pricesIncludeTax) {
        this(name, defaultCurrencyCode, usdToKhrExchangeRate, pricesIncludeTax, null, null);
    }
}
