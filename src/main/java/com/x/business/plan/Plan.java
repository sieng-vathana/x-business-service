package com.x.business.plan;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TinhLuk subscription plans: what each costs, its limits and which features it includes. This is the single
 * source the BFF, portal and backoffice read through GET /api/v1/businesses/plans.
 * A limit of null means unlimited.
 */
public enum Plan {
    STARTER("Starter", "For a single shop getting started with POS and KHQR.",
            new BigDecimal("9"), new BigDecimal("90"), 1, 3, 300, 1,
            EnumSet.noneOf(PlanFeature.class)),
    BUSINESS("Business", "For growing shops that manage stock, customers and staff.",
            new BigDecimal("25"), new BigDecimal("250"), 3, 15, 3000, 3,
            EnumSet.of(PlanFeature.PRODUCT_IMPORT, PlanFeature.INVENTORY, PlanFeature.PURCHASING,
                    PlanFeature.SHIFT_REPORTS, PlanFeature.CUSTOMERS_LOYALTY, PlanFeature.ALL_TELEGRAM_ALERTS,
                    PlanFeature.CUSTOM_ROLES, PlanFeature.FULL_REPORTS)),
    PRO("Pro", "For multi-store businesses that also sell online on the TinhLuk marketplace.",
            new BigDecimal("49"), new BigDecimal("490"), null, null, null, null,
            EnumSet.allOf(PlanFeature.class));

    /** Merchants created before plans existed keep every feature. */
    public static final Plan LEGACY_DEFAULT = PRO;
    public static final int TRIAL_DAYS = 14;

    private final String label;
    private final String tagline;
    private final BigDecimal monthlyPriceUsd;
    private final BigDecimal yearlyPriceUsd;
    private final Integer maxStores;
    private final Integer maxStaff;
    private final Integer maxProducts;
    private final Integer maxTelegramChats;
    private final Set<PlanFeature> features;

    Plan(String label, String tagline, BigDecimal monthlyPriceUsd, BigDecimal yearlyPriceUsd, Integer maxStores,
         Integer maxStaff, Integer maxProducts, Integer maxTelegramChats, Set<PlanFeature> features) {
        this.label = label;
        this.tagline = tagline;
        this.monthlyPriceUsd = monthlyPriceUsd;
        this.yearlyPriceUsd = yearlyPriceUsd;
        this.maxStores = maxStores;
        this.maxStaff = maxStaff;
        this.maxProducts = maxProducts;
        this.maxTelegramChats = maxTelegramChats;
        this.features = features;
    }

    public boolean includes(PlanFeature feature) {
        return features.contains(feature);
    }

    public Integer maxTelegramChats() {
        return maxTelegramChats;
    }

    /** Unknown or missing codes fall back to the legacy default, so an old record never loses features. */
    public static Plan of(String code) {
        if (code == null || code.isBlank()) return LEGACY_DEFAULT;
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return LEGACY_DEFAULT;
        }
    }

    public PlanResponse toResponse() {
        List<PlanResponse.FeatureLine> lines = PlanFeature.DISPLAY.stream()
                .map(line -> new PlanResponse.FeatureLine(line.key(), line.label(),
                        line.feature() == null || features.contains(line.feature())))
                .toList();
        return new PlanResponse(name(), label, tagline, monthlyPriceUsd, yearlyPriceUsd, "USD", TRIAL_DAYS,
                maxStores, maxStaff, maxProducts, maxTelegramChats,
                features.stream().map(Enum::name).sorted().toList(), lines);
    }
}
