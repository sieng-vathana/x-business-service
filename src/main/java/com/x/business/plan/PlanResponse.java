package com.x.business.plan;

import java.math.BigDecimal;
import java.util.List;

/** A plan as clients see it. Limits that are null are unlimited. */
public record PlanResponse(
        String code,
        String name,
        String tagline,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String currencyCode,
        int trialDays,
        Integer maxStores,
        Integer maxStaff,
        Integer maxProducts,
        Integer maxTelegramChats,
        List<String> features,
        List<FeatureLine> comparison) {

    public record FeatureLine(String key, String label, boolean included) {
    }
}
