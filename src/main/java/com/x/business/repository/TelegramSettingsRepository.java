package com.x.business.repository;

import com.x.business.entity.TelegramSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TelegramSettingsRepository extends JpaRepository<TelegramSettings, Long> {
    Optional<TelegramSettings> findByLinkCode(String linkCode);

    List<TelegramSettings> findAllByChatId(Long chatId);

    List<TelegramSettings> findAllByChatIdIsNotNullAndDailySummaryTrue();

    @Transactional
    @Modifying
    @Query("update TelegramSettings s set s.summarySentOn = :day where s.businessId = :businessId")
    void markSummarySent(@Param("businessId") Long businessId, @Param("day") LocalDate day);
}
