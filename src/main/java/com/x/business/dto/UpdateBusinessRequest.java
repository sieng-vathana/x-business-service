package com.x.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBusinessRequest(
        @NotBlank @Size(max = 160) String name) {
}
