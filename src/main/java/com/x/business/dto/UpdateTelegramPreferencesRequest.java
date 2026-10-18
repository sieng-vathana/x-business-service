package com.x.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Alert switches; a null field keeps its current value. */
public record UpdateTelegramPreferencesRequest(
        Boolean khqrAlerts,
        Boolean orderAlerts,
        Boolean lowStockAlerts,
        Boolean dailySummary,
        @Min(value = 0, message = "Low stock threshold cannot be negative")
        @Max(value = 100000, message = "Low stock threshold is too large") Integer lowStockThreshold,
        @Min(value = 0, message = "Daily summary hour must be between 0 and 23")
        @Max(value = 23, message = "Daily summary hour must be between 0 and 23") Integer dailySummaryHour) {
}
