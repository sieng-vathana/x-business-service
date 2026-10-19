package com.x.business.telegram;

/** Kinds of alerts a business can switch on or off for its Telegram chat. */
public enum TelegramAlertType {
    KHQR_PAYMENT,
    ONLINE_ORDER,
    LOW_STOCK,
    DAILY_SUMMARY,
    /** Notices from the platform about the account itself, such as a suspension; every chat gets them. */
    ACCOUNT
}
