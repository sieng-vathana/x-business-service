package com.VyntraBusinessService.service;

import com.VyntraBusinessService.dto.BusinessResponse;
import com.VyntraBusinessService.dto.CreateBusinessRequest;
import com.VyntraBusinessService.entity.Business;
import com.VyntraBusinessService.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

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
                .ownerUserId(request.ownerUserId())
                .name(request.name().trim())
                .code(code)
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
        return businessRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getOwnerUserId(),
                business.getName(),
                business.getCode(),
                business.getStatus(),
                business.getCreatedAt(),
                business.getUpdatedAt());
    }
}
