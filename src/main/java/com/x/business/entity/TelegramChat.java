package com.x.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** A Telegram chat, or one topic of a group with topics, that gets a business's alerts for some or all of its stores. */
@Entity
@Table(
        name = "business_telegram_chats",
        indexes = {
                @Index(name = "idx_telegram_chats_business", columnList = "business_id"),
                @Index(name = "idx_telegram_chats_chat", columnList = "chat_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    /** Topic of a group with topics; null for the chat itself or a group's General topic. */
    @Column(name = "message_thread_id")
    private Long messageThreadId;

    @Column(name = "chat_title", length = 255)
    private String chatTitle;

    /** Telegram's chat type: private, group or supergroup. */
    @Column(name = "chat_type", length = 16)
    private String chatType;

    @Column(name = "topic_title", length = 128)
    private String topicTitle;

    /** Comma-separated ids of the stores whose alerts this chat gets; null for every store. */
    @Column(name = "store_ids", length = 1000)
    private String storeIds;

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

    /** Business-local date of the last daily summary sent here, so each day is sent once. */
    @Column(name = "summary_sent_on")
    private LocalDate summarySentOn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** The chosen stores, or null when the chat gets every store's alerts. */
    public List<Long> storeIdList() {
        if (storeIds == null || storeIds.isBlank()) {
            return null;
        }
        return Arrays.stream(storeIds.split(",")).map(String::trim).filter(id -> !id.isEmpty()).map(Long::valueOf).toList();
    }

    public void useStores(Collection<Long> stores) {
        storeIds = stores == null ? null : stores.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Whether an alert about this store belongs here; alerts without a store go to every chat. */
    public boolean covers(Long storeId) {
        List<Long> stores = storeIdList();
        return stores == null || storeId == null || stores.contains(storeId);
    }
}
