package com.x.business.service;

import com.x.business.dto.CreateBusinessRequest;
import com.x.business.entity.Business;
import com.x.business.repository.BusinessRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessServiceTest {

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
    }
}
