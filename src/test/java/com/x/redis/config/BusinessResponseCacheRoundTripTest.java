package com.x.redis.config;

import com.x.business.dto.BusinessResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Redis cache must read back what it wrote, including the computed suspended flag. */
class BusinessResponseCacheRoundTripTest {

    @Test
    void suspendedMerchantSurvivesTheRedisSerializer() {
        var serializer = XRedisAutoConfiguration.jsonSerializer();
        BusinessResponse business = new BusinessResponse(2L, 2L, "Sota", "SOTA", "USD", new BigDecimal("4100.000000"),
                null, null, null, true, "Asia/Phnom_Penh", 1, 1, LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null, LocalDateTime.of(2026, 10, 19, 10, 0), "Unpaid fees", 9L);

        Object read = serializer.deserialize(serializer.serialize(java.util.List.of(business)));

        assertEquals(java.util.List.of(business), read);
        assertEquals(true, ((java.util.List<?>) read).stream().map(BusinessResponse.class::cast).findFirst().orElseThrow().suspended());
    }
}
