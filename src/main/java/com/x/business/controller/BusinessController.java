package com.x.business.controller;

import com.x.business.dto.BusinessResponse;
import com.x.business.dto.CreateBusinessRequest;
import com.x.business.dto.ExchangeRateReferenceResponse;
import com.x.business.dto.UpdateBusinessRequest;
import com.x.business.service.BusinessService;
import com.x.business.service.ExchangeRateReferenceService;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;
    private final ExchangeRateReferenceService exchangeRateReferenceService;

    @PostMapping
    public ResponseEntity<ApiResponse<BusinessResponse>> create(@Valid @RequestBody CreateBusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Business created", businessService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BusinessResponse>> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateBusinessRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Business updated", businessService.update(id, request)));
    }

    /** Used only to compensate a failed cross-service workspace registration. */
    @DeleteMapping("/{id}/registration-failure")
    public ResponseEntity<Void> deleteFailedRegistration(@PathVariable @Positive Long id) {
        businessService.deleteFailedRegistration(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BusinessResponse>> getById(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), businessService.getById(id)));
    }

    @GetMapping("/{id}/exchange-rate-reference")
    public ResponseEntity<ApiResponse<ExchangeRateReferenceResponse>> getExchangeRateReference(
            @PathVariable @Positive Long id) {
        businessService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                exchangeRateReferenceService.latestStored()));
    }

    @PostMapping("/{id}/exchange-rate-reference/refresh")
    public ResponseEntity<ApiResponse<ExchangeRateReferenceResponse>> refreshExchangeRateReference(
            @PathVariable @Positive Long id) {
        businessService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                exchangeRateReferenceService.refreshLatest()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessResponse>>> getByOwner(@RequestParam @Positive Long ownerUserId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), businessService.getByOwner(ownerUserId)));
    }

    @GetMapping("/approvals/pending")
    public ResponseEntity<ApiResponse<List<BusinessResponse>>> pendingApprovals() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), businessService.getPendingApprovals()));
    }

    @PostMapping("/{id}/approval/approve")
    public ResponseEntity<ApiResponse<BusinessResponse>> approve(
            @PathVariable @Positive Long id, @RequestParam @Positive Long reviewerUserId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                "Business approved", businessService.approve(id, reviewerUserId)));
    }

    @PostMapping("/{id}/approval/reject")
    public ResponseEntity<ApiResponse<BusinessResponse>> reject(
            @PathVariable @Positive Long id, @RequestParam @Positive Long reviewerUserId,
            @Valid @RequestBody ReviewBusinessRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                "Business rejected", businessService.reject(id, reviewerUserId, request.reason())));
    }

    @PostMapping("/{id}/approval/resubmit")
    public ResponseEntity<ApiResponse<BusinessResponse>> resubmit(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                "Business submitted for review", businessService.resubmit(id)));
    }

    /** All merchants for the platform backoffice; status is pending, approved, rejected or suspended. */
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<BusinessResponse>>> listForAdmin(
            @RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), businessService.listForAdmin(status, search)));
    }

    @PostMapping("/{id}/suspension")
    public ResponseEntity<ApiResponse<BusinessResponse>> suspend(
            @PathVariable @Positive Long id, @RequestParam @Positive Long actorUserId,
            @Valid @RequestBody ReviewBusinessRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                "Merchant suspended", businessService.suspend(id, actorUserId, request.reason())));
    }

    @DeleteMapping("/{id}/suspension")
    public ResponseEntity<ApiResponse<BusinessResponse>> reinstate(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                "Merchant reinstated", businessService.reinstate(id)));
    }

    public record ReviewBusinessRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 1000) String reason) {}
}
