package com.x.business.repository;

import com.x.business.entity.TelegramChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TelegramChatRepository extends JpaRepository<TelegramChat, Long> {
    List<TelegramChat> findAllByBusinessIdOrderByIdAsc(Long businessId);

    List<TelegramChat> findAllByChatId(Long chatId);

    Optional<TelegramChat> findByIdAndBusinessId(Long id, Long businessId);

    List<TelegramChat> findAllByDailySummaryTrue();

    @Transactional
    @Modifying
    @Query("update TelegramChat c set c.summarySentOn = :day where c.id = :id")
    void markSummarySent(@Param("id") Long id, @Param("day") LocalDate day);
}
