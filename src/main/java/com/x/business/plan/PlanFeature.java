package com.x.business.plan;

import java.util.List;

/** Features that differ between plans. Everything else (POS, KHQR, receipts, 2FA...) is in every plan. */
public enum PlanFeature {
    PRODUCT_IMPORT,
    INVENTORY,
    PURCHASING,
    SHIFT_REPORTS,
    CUSTOMERS_LOYALTY,
    ALL_TELEGRAM_ALERTS,
    CUSTOM_ROLES,
    FULL_REPORTS,
    MARKETPLACE,
    DELIVERY,
    PRIORITY_SUPPORT;

    /** One line of the comparison table; feature null means the line is in every plan. */
    public record Line(String key, String label, PlanFeature feature) {
    }

    /** The comparison shown on sign-up, in the portal and in the backoffice, in this order. */
    public static final List<Line> DISPLAY = List.of(
            new Line("POS", "POS with cash in USD and riel", null),
            new Line("KHQR", "KHQR (Bakong) payments, confirmed automatically", null),
            new Line("RECEIPTS", "Receipts, held sales and refunds", null),
            new Line("CASH_DRAWER", "Cash drawer open and close", null),
            new Line("KHMER", "Khmer and English portal", null),
            new Line("TWO_FACTOR", "Two-step sign-in by Telegram or email", null),
            new Line("PAYMENT_ALERTS", "Telegram payment alerts", null),
            new Line("BASIC_REPORTS", "Dashboard and sales history", null),
            new Line(PRODUCT_IMPORT.name(), "Product import from Excel or CSV", PRODUCT_IMPORT),
            new Line(INVENTORY.name(), "Inventory: stock movement, low stock, expiry, transfers", INVENTORY),
            new Line(PURCHASING.name(), "Purchasing: suppliers, purchase orders, receiving", PURCHASING),
            new Line(SHIFT_REPORTS.name(), "Cash register shift history and end-of-day", SHIFT_REPORTS),
            new Line(CUSTOMERS_LOYALTY.name(), "Customers and loyalty points", CUSTOMERS_LOYALTY),
            new Line(ALL_TELEGRAM_ALERTS.name(), "All Telegram alerts: orders, low stock, daily summary", ALL_TELEGRAM_ALERTS),
            new Line(CUSTOM_ROLES.name(), "Custom staff roles and permissions", CUSTOM_ROLES),
            new Line(FULL_REPORTS.name(), "Full reports: products, payments, stores, tax", FULL_REPORTS),
            new Line(MARKETPLACE.name(), "TinhLuk marketplace listing and online orders", MARKETPLACE),
            new Line(DELIVERY.name(), "Delivery booking", DELIVERY),
            new Line(PRIORITY_SUPPORT.name(), "Priority support", PRIORITY_SUPPORT));
}
