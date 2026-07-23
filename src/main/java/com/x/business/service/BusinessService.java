package com.x.business.service;

import com.x.business.dto.BusinessResponse;
import com.x.business.dto.CreateBusinessRequest;
import com.x.business.entity.Business;
import com.x.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Currency;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private static final int ACTIVE_STATUS = 1;

    private final BusinessRepository businessRepository;

    @Transactional
    public BusinessResponse create(CreateBusinessRequest request) {
        String code = normalizeCode(request.code());
        if (businessRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business code already exists");
        }

        Business business = Business.builder()
                .userId(request.ownerUserId())
                .name(request.name().trim())
                .code(code)
                .defaultCurrencyCode(normalizeCurrencyCode(request.defaultCurrencyCode()))
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

    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        return businessRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
    }

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
}
