package com.VyntraBusinessService.dto;

import java.time.LocalDateTime;

public record BusinessResponse(
        Long id,
        Long ownerUserId,
        String name,
        String code,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
