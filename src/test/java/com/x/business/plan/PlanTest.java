package com.x.business.plan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    @Test
    void pricesAndLimitsMatchTheAgreedPlans() {
        assertEquals(new BigDecimal("9"), Plan.STARTER.toResponse().monthlyPrice());
        assertEquals(new BigDecimal("250"), Plan.BUSINESS.toResponse().yearlyPrice());
        assertEquals(1, Plan.STARTER.toResponse().maxStores());
        assertEquals(15, Plan.BUSINESS.toResponse().maxStaff());
        assertNull(Plan.PRO.toResponse().maxProducts());
        assertEquals(14, Plan.PRO.toResponse().trialDays());
    }

    @Test
    void featuresGrowWithThePlan() {
        assertFalse(Plan.STARTER.includes(PlanFeature.INVENTORY));
        assertTrue(Plan.BUSINESS.includes(PlanFeature.INVENTORY));
        assertFalse(Plan.BUSINESS.includes(PlanFeature.MARKETPLACE));
        assertTrue(Plan.PRO.includes(PlanFeature.MARKETPLACE));
        var starterLines = Plan.STARTER.toResponse().comparison();
        assertTrue(starterLines.stream().filter(line -> line.key().equals("KHQR")).findFirst().orElseThrow().included());
        assertFalse(starterLines.stream().filter(line -> line.key().equals("PURCHASING")).findFirst().orElseThrow().included());
    }

    @Test
    void missingOrUnknownPlanKeepsEveryFeature() {
        assertEquals(Plan.PRO, Plan.of(null));
        assertEquals(Plan.PRO, Plan.of("gold"));
        assertEquals(Plan.BUSINESS, Plan.of("business"));
    }
}
