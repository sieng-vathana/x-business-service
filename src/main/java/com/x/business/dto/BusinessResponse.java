package com.x.business.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** suspended is computed from suspendedAt: written to JSON, ignored when read back (for example from the Redis cache). */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value = "suspended", allowGetters = true)
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
        LocalDateTime reviewedAt,
        LocalDateTime suspendedAt,
        String suspensionReason,
        Long suspendedByUserId,
        String plan,
        String billingCycle,
        LocalDateTime trialEndsAt) {

    public BusinessResponse(
            Long id, Long ownerUserId, String name, String code, String defaultCurrencyCode,
            BigDecimal usdToKhrExchangeRate, String taxRegistrationNumber, String taxRegistrationLabel,
            Long defaultTaxId, Boolean pricesIncludeTax, String timeZone, Integer fiscalYearStartMonth,
            Integer status, LocalDateTime createdAt, LocalDateTime updatedAt, String bakongAccountId,
            String reviewReason, Long reviewedByUserId, LocalDateTime reviewedAt,
            LocalDateTime suspendedAt, String suspensionReason, Long suspendedByUserId) {
        this(id, ownerUserId, name, code, defaultCurrencyCode, usdToKhrExchangeRate, taxRegistrationNumber,
                taxRegistrationLabel, defaultTaxId, pricesIncludeTax, timeZone, fiscalYearStartMonth, status,
                createdAt, updatedAt, bakongAccountId, reviewReason, reviewedByUserId, reviewedAt,
                suspendedAt, suspensionReason, suspendedByUserId, null, null, null);
    }

    /** Suspended by the platform: no sign-in, sales or marketplace listing until reinstated. */
    @com.fasterxml.jackson.annotation.JsonProperty("suspended")
    public boolean suspended() {
        return suspendedAt != null;
    }

    public BusinessResponse(
            Long id, Long ownerUserId, String name, String code, String defaultCurrencyCode,
            BigDecimal usdToKhrExchangeRate, String taxRegistrationNumber, String taxRegistrationLabel,
            Long defaultTaxId, Boolean pricesIncludeTax, String timeZone, Integer fiscalYearStartMonth,
            Integer status, LocalDateTime createdAt, LocalDateTime updatedAt, String bakongAccountId,
            String reviewReason, Long reviewedByUserId, LocalDateTime reviewedAt) {
        this(id, ownerUserId, name, code, defaultCurrencyCode, usdToKhrExchangeRate, taxRegistrationNumber,
                taxRegistrationLabel, defaultTaxId, pricesIncludeTax, timeZone, fiscalYearStartMonth, status,
                createdAt, updatedAt, bakongAccountId, reviewReason, reviewedByUserId, reviewedAt, null, null, null);
    }

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
