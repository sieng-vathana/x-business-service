package com.x.business.telegram;

import com.x.business.dto.UpdateTelegramPreferencesRequest;
import com.x.business.entity.Business;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramServiceTest {

    private TelegramSettingsRepository settingsRepository;
    private BusinessRepository businessRepository;
    private TelegramClient telegramClient;
    private TelegramService service;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(TelegramSettingsRepository.class);
        businessRepository = mock(BusinessRepository.class);
        telegramClient = mock(TelegramClient.class);
        when(telegramClient.isConfigured()).thenReturn(true);
        when(telegramClient.botUsername()).thenReturn("TinhLokAlertsBot");
        when(businessRepository.findById(2L)).thenReturn(Optional.of(Business.builder().id(2L).name("Sota").build()));
        when(settingsRepository.save(any(TelegramSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TelegramService(settingsRepository, businessRepository, telegramClient);
    }

    @Test
    void linkCodeFromStartConnectsTheChatOnce() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));

        var link = service.createLink(2L);
        String code = settings.getLinkCode();
        when(settingsRepository.findByLinkCode(code)).thenReturn(Optional.of(settings));

        assertEquals("https://t.me/TinhLokAlertsBot?start=" + code, link.privateChatUrl());
        assertEquals("https://t.me/TinhLokAlertsBot?startgroup=" + code, link.groupChatUrl());
        assertEquals(Optional.of("Sota"), service.linkChat(code, -100123L, null, "Sota staff"));
        assertEquals(-100123L, settings.getChatId());
        assertEquals("Sota staff", settings.getChatTitle());
        assertNull(settings.getLinkCode());
    }

    @Test
    void expiredLinkCodeDoesNotConnect() {
        TelegramSettings settings = TelegramSettings.builder()
                .businessId(2L).linkCode("old").linkCodeExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(settingsRepository.findByLinkCode("old")).thenReturn(Optional.of(settings));

        assertEquals(Optional.empty(), service.linkChat("old", 55L, null, "Owner"));
        assertNull(settings.getChatId());
    }

    @Test
    void alertsFollowTheBusinessSwitches() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).chatId(55L).build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        service.updatePreferences(2L, new UpdateTelegramPreferencesRequest(null, false, null, null, 3, null));

        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, "paid"));
        assertFalse(service.notify(2L, TelegramAlertType.ONLINE_ORDER, "order"));
        assertEquals(3, settings.getLowStockThreshold());
        verify(telegramClient).sendMessage(55L, null, "paid");
        verify(telegramClient, never()).sendMessage(55L, null, "order");
    }

    @Test
    void chatThatBlockedTheBotIsDisconnected() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).chatId(55L).chatTitle("Owner").build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        when(settingsRepository.findAllByChatId(55L)).thenReturn(List.of(settings));
        doThrow(new TelegramClient.TelegramException("Forbidden: bot was blocked by the user", 403))
                .when(telegramClient).sendMessage(anyLong(), any(), anyString());

        assertFalse(service.notify(2L, TelegramAlertType.LOW_STOCK, "low"));
        assertNull(settings.getChatId());
        verify(settingsRepository).saveAll(List.of(settings));
    }

    @Test
    void alertsGoToTheChosenTopic() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).chatId(-100777L).build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        when(settingsRepository.findAllByChatId(-100777L)).thenReturn(List.of(settings));

        assertEquals(List.of("Sota"), service.useTopic(-100777L, 42L));
        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, "paid"));
        verify(telegramClient).sendMessage(-100777L, 42L, "paid");
    }

    @Test
    void closedTopicExplainsHowToPickAnother() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).chatId(-100777L).build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        doThrow(new TelegramClient.TelegramException("Bad Request: TOPIC_CLOSED", 400))
                .when(telegramClient).sendMessage(anyLong(), any(), anyString());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.sendTest(2L));
        assertEquals(TelegramService.TOPIC_UNAVAILABLE, exception.getReason());
        assertEquals(-100777L, settings.getChatId());
    }

    @Test
    void nothingIsSentWithoutABotToken() {
        when(telegramClient.isConfigured()).thenReturn(false);

        assertFalse(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, "paid"));
        verify(telegramClient, never()).sendMessage(anyLong(), any(), anyString());
    }
}
