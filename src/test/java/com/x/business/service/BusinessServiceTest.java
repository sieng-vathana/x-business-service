package com.x.business.service;

import com.x.business.dto.CreateBusinessRequest;
import com.x.business.entity.Business;
import com.x.business.repository.BusinessRepository;
import com.x.redis.cache.CacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

class BusinessServiceTest {

    @Test
    void cachesBusinessesByOwnerUsingTheOwnerId() throws NoSuchMethodException {
        Cacheable cacheable = BusinessService.class
                .getMethod("getByOwner", Long.class)
                .getAnnotation(Cacheable.class);

        assertEquals(List.of(CacheNames.BUSINESSES_BY_OWNER), List.of(cacheable.cacheNames()));
        assertEquals("#ownerUserId", cacheable.key());
    }

    @Test
    void createNormalizesCodeAndKeepsOwnerReference() {
        BusinessRepository repository = mock(BusinessRepository.class);
        when(repository.existsByCode("ACME")).thenReturn(false);
        when(repository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(1L);
            return business;
        });
        BusinessService service = new BusinessService(repository);

        var response = service.create(new CreateBusinessRequest(
                7L, "Acme Trading", " acme ", "khr", "VAT-001", "VAT", null,
                true, "Asia/Phnom_Penh", 1));

        assertEquals(7L, response.ownerUserId());
        assertEquals("ACME", response.code());
        assertEquals("KHR", response.defaultCurrencyCode());
        assertEquals(0, response.status());
    }

    @Test
    void onlyPendingBusinessesCanBeApproved() {
        BusinessRepository repository = mock(BusinessRepository.class);
        Business business = Business.builder().id(3L).status(0).build();
        when(repository.findById(3L)).thenReturn(Optional.of(business));
        when(repository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BusinessService service = new BusinessService(repository);

        var approved = service.approve(3L, 9L);
        assertEquals(1, approved.status());
        assertEquals(9L, approved.reviewedByUserId());
        assertThrows(ResponseStatusException.class, () -> service.approve(3L, 9L));
    }

    @Test
    void rejectedBusinessCanBeResubmitted() {
        BusinessRepository repository = mock(BusinessRepository.class);
        Business business = Business.builder().id(4L).status(0).build();
        when(repository.findById(4L)).thenReturn(Optional.of(business));
        when(repository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BusinessService service = new BusinessService(repository);

        var rejected = service.reject(4L, 9L, "Missing registration details");
        assertEquals(-1, rejected.status());
        assertEquals("Missing registration details", rejected.reviewReason());

        var resubmitted = service.resubmit(4L);
        assertEquals(0, resubmitted.status());
        assertEquals(null, resubmitted.reviewReason());
    }
}
