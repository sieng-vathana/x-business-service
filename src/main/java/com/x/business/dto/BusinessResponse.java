package com.x.business.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record BusinessResponse(
        Long id,
        Long ownerUserId,
        String name,
        String code,
        String defaultCurrencyCode,
        BigDecimal usdToKhrExchangeRate,
        String taxRegistrationNumber,
        String taxRegistrationLabel,
        Long defaultTaxId,
        Boolean pricesIncludeTax,
        String timeZone,
        Integer fiscalYearStartMonth,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String bakongAccountId,
        String reviewReason,
        Long reviewedByUserId,
        LocalDateTime reviewedAt) {

    public BusinessResponse(
            Long id, Long ownerUserId, String name, String code, String defaultCurrencyCode,
            BigDecimal usdToKhrExchangeRate, String taxRegistrationNumber, String taxRegistrationLabel,
            Long defaultTaxId, Boolean pricesIncludeTax, String timeZone, Integer fiscalYearStartMonth,
            Integer status, LocalDateTime createdAt, LocalDateTime updatedAt, String bakongAccountId) {
        this(id, ownerUserId, name, code, defaultCurrencyCode, usdToKhrExchangeRate, taxRegistrationNumber,
                taxRegistrationLabel, defaultTaxId, pricesIncludeTax, timeZone, fiscalYearStartMonth, status,
                createdAt, updatedAt, bakongAccountId, null, null, null);
    }

    public BusinessResponse(
            Long id, Long ownerUserId, String name, String code, String defaultCurrencyCode,
            BigDecimal usdToKhrExchangeRate, String taxRegistrationNumber, String taxRegistrationLabel,
            Long defaultTaxId, Boolean pricesIncludeTax, String timeZone, Integer fiscalYearStartMonth,
            Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, ownerUserId, name, code, defaultCurrencyCode, usdToKhrExchangeRate, taxRegistrationNumber,
                taxRegistrationLabel, defaultTaxId, pricesIncludeTax, timeZone, fiscalYearStartMonth, status,
                createdAt, updatedAt, null, null, null, null);
    }

    public BusinessResponse(
            Long id, Long ownerUserId, String name, String code, String defaultCurrencyCode,
            String taxRegistrationNumber, String taxRegistrationLabel, Long defaultTaxId,
            Boolean pricesIncludeTax, String timeZone, Integer fiscalYearStartMonth, Integer status,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, ownerUserId, name, code, defaultCurrencyCode, new BigDecimal("4000.000000"),
                taxRegistrationNumber, taxRegistrationLabel, defaultTaxId, pricesIncludeTax, timeZone,
                fiscalYearStartMonth, status, createdAt, updatedAt);
    }
}
