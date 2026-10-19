package com.x.business.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExchangeRateReferenceResponse(
        String baseCurrency,
        String targetCurrency,
        BigDecimal rate,
        LocalDate rateDate,
        Instant fetchedAt,
        String provider,
        boolean stale) {
}
