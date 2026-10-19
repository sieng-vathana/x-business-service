package com.x.business.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Stores and alert switches of one chat; a null field keeps its current value. With allStores false, storeIds lists the stores. */
public record UpdateTelegramChatRequest(
        Boolean allStores,
        @Size(max = 200, message = "Choose at most 200 stores") List<@Positive Long> storeIds,
        Boolean khqrAlerts,
        Boolean orderAlerts,
        Boolean lowStockAlerts,
        Boolean dailySummary) {
}
