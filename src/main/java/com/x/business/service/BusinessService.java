package com.x.business.service;

import com.x.business.dto.BusinessResponse;
import com.x.business.dto.CreateBusinessRequest;
import com.x.business.dto.UpdateBusinessRequest;
import com.x.business.entity.Business;
import com.x.business.repository.BusinessRepository;
import com.x.redis.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Currency;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private static final int ACTIVE_STATUS = 1;
    private static final BigDecimal DEFAULT_USD_TO_KHR_RATE = new BigDecimal("4000.000000");

    private final BusinessRepository businessRepository;

    @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, key = "#request.ownerUserId()")
    @Transactional
    public BusinessResponse create(CreateBusinessRequest request) {
        String code = normalizeCode(request.code());
        if (businessRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business code already exists");
        }

        String currency = normalizeCurrencyCode(request.defaultCurrencyCode());
        Business business = Business.builder()
                .userId(request.ownerUserId())
                .name(request.name().trim())
                .code(code)
                .defaultCurrencyCode(currency)
                .usdToKhrExchangeRate(normalizeExchangeRate(request.usdToKhrExchangeRate()))
                .taxRegistrationNumber(trimToNull(request.taxRegistrationNumber()))
                .taxRegistrationLabel(trimToNull(request.taxRegistrationLabel()))
                .taxId(request.defaultTaxId())
                .pricesIncludeTax(request.pricesIncludeTax() == null || request.pricesIncludeTax())
                .timeZone(normalizeTimeZone(request.timeZone()))
                .fiscalYearStartMonth(request.fiscalYearStartMonth())
                .status(ACTIVE_STATUS)
                .build();
        return toResponse(businessRepository.save(business));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse update(Long id, UpdateBusinessRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        business.setName(request.name().trim());
        if (request.defaultCurrencyCode() != null && !request.defaultCurrencyCode().isBlank()) {
            business.setDefaultCurrencyCode(normalizeCurrencyCode(request.defaultCurrencyCode()));
        }
        if (request.usdToKhrExchangeRate() != null) {
            business.setUsdToKhrExchangeRate(normalizeExchangeRate(request.usdToKhrExchangeRate()));
        } else if (business.getUsdToKhrExchangeRate() == null || business.getUsdToKhrExchangeRate().signum() <= 0) {
            business.setUsdToKhrExchangeRate(DEFAULT_USD_TO_KHR_RATE);
        }
        if (request.pricesIncludeTax() != null) {
            business.setPricesIncludeTax(request.pricesIncludeTax());
        }
        if (request.taxRegistrationNumber() != null) {
            business.setTaxRegistrationNumber(trimToNull(request.taxRegistrationNumber()));
        }
        if (request.taxRegistrationLabel() != null) {
            business.setTaxRegistrationLabel(trimToNull(request.taxRegistrationLabel()));
        }
        return toResponse(businessRepository.save(business));
    }

    @Cacheable(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id")
    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        return businessRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    @Cacheable(cacheNames = CacheNames.BUSINESSES_BY_OWNER, key = "#ownerUserId")
    @Transactional(readOnly = true)
    public List<BusinessResponse> getByOwner(Long ownerUserId) {
        return businessRepository.findAllByUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrencyCode(String currencyCode) {
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default currency code must be a valid ISO 4217 code");
        }
    }

    private String normalizeTimeZone(String timeZone) {
        String normalized = timeZone.trim();
        try {
            return ZoneId.of(normalized).getId();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time zone must be a valid IANA time-zone ID");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getUserId(),
                business.getName(),
                business.getCode(),
                business.getDefaultCurrencyCode(),
                business.getUsdToKhrExchangeRate() == null || business.getUsdToKhrExchangeRate().signum() <= 0
                        ? DEFAULT_USD_TO_KHR_RATE
                        : business.getUsdToKhrExchangeRate(),
                business.getTaxRegistrationNumber(),
                business.getTaxRegistrationLabel(),
                business.getTaxId(),
                business.getPricesIncludeTax(),
                business.getTimeZone(),
                business.getFiscalYearStartMonth(),
                business.getStatus(),
                business.getCreatedAt(),
                business.getUpdatedAt());
    }

    private BigDecimal normalizeExchangeRate(BigDecimal rate) {
        BigDecimal normalized = rate == null ? DEFAULT_USD_TO_KHR_RATE : rate;
        if (normalized.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USD to KHR exchange rate must be positive");
        }
        return normalized.setScale(6, RoundingMode.HALF_UP);
    }
}
