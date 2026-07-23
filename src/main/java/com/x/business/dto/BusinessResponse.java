package com.x.business.dto;

import java.time.LocalDateTime;

public record BusinessResponse(
        Long id,
        Long ownerUserId,
        String name,
        String code,
        String defaultCurrencyCode,
        String taxRegistrationNumber,
        String taxRegistrationLabel,
        Long defaultTaxId,
        Boolean pricesIncludeTax,
        String timeZone,
        Integer fiscalYearStartMonth,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
