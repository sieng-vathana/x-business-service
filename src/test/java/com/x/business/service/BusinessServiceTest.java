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

    @Test
    void suspensionKeepsApprovalAndNeedsAReason() {
        BusinessRepository repository = mock(BusinessRepository.class);
        Business business = Business.builder().id(5L).status(1).build();
        when(repository.findById(5L)).thenReturn(Optional.of(business));
        when(repository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BusinessService service = new BusinessService(repository);

        assertThrows(ResponseStatusException.class, () -> service.suspend(5L, 9L, "  "));

        var suspended = service.suspend(5L, 9L, " Fraud reports from customers ");
        assertEquals(true, suspended.suspended());
        assertEquals("Fraud reports from customers", suspended.suspensionReason());
        assertEquals(9L, suspended.suspendedByUserId());
        assertEquals(1, suspended.status());
        assertThrows(ResponseStatusException.class, () -> service.suspend(5L, 9L, "again"));

        var reinstated = service.reinstate(5L);
        assertEquals(false, reinstated.suspended());
        assertEquals(null, reinstated.suspensionReason());
        assertEquals(1, reinstated.status());
        assertThrows(ResponseStatusException.class, () -> service.reinstate(5L));
    }

    @Test
    void adminListFiltersByStatusAndSearch() {
        BusinessRepository repository = mock(BusinessRepository.class);
        Business approved = Business.builder().id(1L).name("Sota Coffee").code("SOTA").status(1).build();
        Business pending = Business.builder().id(2L).name("Kampot Pepper").code("KMP").status(0).build();
        Business suspended = Business.builder().id(3L).name("Night Market").code("NIGHT").status(1)
                .suspendedAt(java.time.LocalDateTime.now()).suspensionReason("Unpaid fees").build();
        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(java.util.List.of(approved, pending, suspended));
        BusinessService service = new BusinessService(repository);

        assertEquals(java.util.List.of(3L), service.listForAdmin("suspended", null).stream().map(b -> b.id()).toList());
        assertEquals(java.util.List.of(1L), service.listForAdmin("approved", null).stream().map(b -> b.id()).toList());
        assertEquals(java.util.List.of(2L), service.listForAdmin("", "kampot").stream().map(b -> b.id()).toList());
        assertEquals(3, service.listForAdmin(null, null).size());
    }
}
