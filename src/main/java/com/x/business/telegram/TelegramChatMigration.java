package com.x.business.telegram;

import com.x.business.entity.TelegramChat;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.TelegramChatRepository;
import com.x.business.repository.TelegramSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A business used to have one chat, stored with its settings. On start, such a chat becomes the
 * business's first row in business_telegram_chats, with its topic and alert switches, for all stores.
 */
@Component
@RequiredArgsConstructor
public class TelegramChatMigration {

    private static final Logger log = LoggerFactory.getLogger(TelegramChatMigration.class);

    private final TelegramSettingsRepository settingsRepository;
    private final TelegramChatRepository chatRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void moveSingleChats() {
        List<TelegramSettings> connected = settingsRepository.findAllByChatIdIsNotNull();
        for (TelegramSettings settings : connected) {
            boolean alreadyMoved = chatRepository.findAllByChatId(settings.getChatId()).stream()
                    .anyMatch(chat -> settings.getBusinessId().equals(chat.getBusinessId()));
            if (!alreadyMoved) {
                chatRepository.save(TelegramChat.builder()
                        .businessId(settings.getBusinessId())
                        .chatId(settings.getChatId())
                        .messageThreadId(settings.getMessageThreadId())
                        .chatTitle(settings.getChatTitle())
                        .chatType(settings.getChatId() < 0 ? "supergroup" : "private")
                        .khqrAlerts(settings.getKhqrAlerts())
                        .orderAlerts(settings.getOrderAlerts())
                        .lowStockAlerts(settings.getLowStockAlerts())
                        .dailySummary(settings.getDailySummary())
                        .summarySentOn(settings.getSummarySentOn())
                        .build());
            }
            settings.setChatId(null);
            settings.setChatTitle(null);
            settings.setMessageThreadId(null);
        }
        settingsRepository.saveAll(connected);
        if (!connected.isEmpty()) {
            log.info("Moved {} Telegram chats to business_telegram_chats", connected.size());
        }
    }
}
