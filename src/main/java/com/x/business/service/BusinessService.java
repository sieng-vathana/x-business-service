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
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Currency;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private static final int PENDING_STATUS = 0;
    private static final int ACTIVE_STATUS = 1;
    private static final int REJECTED_STATUS = -1;
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
                .status(PENDING_STATUS)
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
        if (request.bakongAccountId() != null) {
            business.setBakongAccountId(trimToNull(request.bakongAccountId()));
        }
        return toResponse(businessRepository.save(business));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public void deleteFailedRegistration(Long id) {
        businessRepository.deleteById(id);
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

    @Transactional(readOnly = true)
    public List<BusinessResponse> getPendingApprovals() {
        return businessRepository.findAllByStatusOrderByCreatedAtAsc(PENDING_STATUS).stream()
                .map(this::toResponse).toList();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse approve(Long id, Long reviewerUserId) {
        Business business = pendingBusiness(id);
        business.setStatus(ACTIVE_STATUS);
        business.setReviewReason(null);
        business.setReviewedByUserId(reviewerUserId);
        business.setReviewedAt(LocalDateTime.now());
        return toResponse(businessRepository.save(business));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse reject(Long id, Long reviewerUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        }
        Business business = pendingBusiness(id);
        business.setStatus(REJECTED_STATUS);
        business.setReviewReason(reason.trim());
        business.setReviewedByUserId(reviewerUserId);
        business.setReviewedAt(LocalDateTime.now());
        return toResponse(businessRepository.save(business));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse resubmit(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getStatus() == null || business.getStatus() != REJECTED_STATUS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only rejected businesses can be resubmitted");
        }
        business.setStatus(PENDING_STATUS);
        business.setReviewReason(null);
        business.setReviewedByUserId(null);
        business.setReviewedAt(null);
        return toResponse(businessRepository.save(business));
    }

    /**
     * Every merchant for the platform backoffice, newest first. filter is "pending", "approved", "rejected" or
     * "suspended"; search matches the name or code.
     */
    @Transactional(readOnly = true)
    public List<BusinessResponse> listForAdmin(String filter, String search) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String wanted = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return businessRepository.findAll(org.springframework.data.domain.Sort.by("createdAt").descending()).stream()
                .filter(business -> switch (wanted) {
                    case "suspended" -> business.getSuspendedAt() != null;
                    case "pending" -> business.getSuspendedAt() == null && Integer.valueOf(PENDING_STATUS).equals(business.getStatus());
                    case "approved" -> business.getSuspendedAt() == null && Integer.valueOf(ACTIVE_STATUS).equals(business.getStatus());
                    case "rejected" -> business.getSuspendedAt() == null && Integer.valueOf(REJECTED_STATUS).equals(business.getStatus());
                    default -> true;
                })
                .filter(business -> query.isEmpty()
                        || business.getName().toLowerCase(Locale.ROOT).contains(query)
                        || business.getCode().toLowerCase(Locale.ROOT).contains(query))
                .map(this::toResponse)
                .toList();
    }

    /** Suspends a merchant with a reason. Its approval status is kept, so reinstating returns it to where it was. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse suspend(Long id, Long actorUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A suspension reason is required");
        }
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getSuspendedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This merchant is already suspended");
        }
        business.setSuspendedAt(LocalDateTime.now());
        business.setSuspensionReason(reason.trim());
        business.setSuspendedByUserId(actorUserId);
        return toResponse(businessRepository.save(business));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BUSINESS_BY_ID, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.BUSINESSES_BY_OWNER, allEntries = true)
    })
    @Transactional
    public BusinessResponse reinstate(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getSuspendedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This merchant is not suspended");
        }
        business.setSuspendedAt(null);
        business.setSuspensionReason(null);
        business.setSuspendedByUserId(null);
        return toResponse(businessRepository.save(business));
    }

    private Business pendingBusiness(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getStatus() == null || business.getStatus() != PENDING_STATUS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending businesses can be reviewed");
        }
        return business;
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
                business.getUpdatedAt(),
                business.getBakongAccountId(),
                business.getReviewReason(),
                business.getReviewedByUserId(),
                business.getReviewedAt(),
                business.getSuspendedAt(),
                business.getSuspensionReason(),
                business.getSuspendedByUserId());
    }

    private BigDecimal normalizeExchangeRate(BigDecimal rate) {
        BigDecimal normalized = rate == null ? DEFAULT_USD_TO_KHR_RATE : rate;
        if (normalized.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USD to KHR exchange rate must be positive");
        }
        return normalized.setScale(6, RoundingMode.HALF_UP);
    }
}
