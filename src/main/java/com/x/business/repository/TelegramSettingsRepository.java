package com.x.business.repository;

import com.x.business.entity.TelegramSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramSettingsRepository extends JpaRepository<TelegramSettings, Long> {
    Optional<TelegramSettings> findByLinkCode(String linkCode);

    /** Businesses connected before chats moved to business_telegram_chats. */
    List<TelegramSettings> findAllByChatIdIsNotNull();
}
