package com.x.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Telegram settings shared by a business's chats: the pending link code, the low stock threshold and the summary hour.
 * The chats themselves are in business_telegram_chats; chatId, chatTitle, messageThreadId, the alert switches and
 * summarySentOn are what a business had before it could connect several chats, read once by TelegramChatMigration.
 */
@Entity
@Table(
        name = "business_telegram_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_telegram_link_code", columnNames = "link_code"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramSettings {

    @Id
    @Column(name = "business_id")
    private Long businessId;

    /** The single chat of before; moved to business_telegram_chats on start. */
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "chat_title", length = 255)
    private String chatTitle;

    /** Topic of a group with topics that receives the alerts; null for the chat itself or a group's General topic. */
    @Column(name = "message_thread_id")
    private Long messageThreadId;

    /** One-time code in the t.me start link, valid until linkCodeExpiresAt. */
    @Column(name = "link_code", length = 32)
    private String linkCode;

    @Column(name = "link_code_expires_at")
    private LocalDateTime linkCodeExpiresAt;

    @Column(name = "khqr_alerts", nullable = false)
    @Builder.Default
    private Boolean khqrAlerts = true;

    @Column(name = "order_alerts", nullable = false)
    @Builder.Default
    private Boolean orderAlerts = true;

    @Column(name = "low_stock_alerts", nullable = false)
    @Builder.Default
    private Boolean lowStockAlerts = true;

    @Column(name = "daily_summary", nullable = false)
    @Builder.Default
    private Boolean dailySummary = true;

    /** A low stock alert is sent when a sale leaves this many units or fewer available. */
    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    /** Hour of the day (0-23, business time zone) when the daily summary is sent. */
    @Column(name = "daily_summary_hour", nullable = false)
    @Builder.Default
    private Integer dailySummaryHour = 21;

    /** Business-local date of the last daily summary, so each day is sent once. */
    @Column(name = "summary_sent_on")
    private LocalDate summarySentOn;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
