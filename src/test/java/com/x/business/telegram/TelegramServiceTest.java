package com.x.business.telegram;

import com.x.business.dto.TelegramStatusResponse;
import com.x.business.dto.UpdateTelegramChatRequest;
import com.x.business.entity.Business;
import com.x.business.entity.TelegramChat;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramChatRepository;
import com.x.business.repository.TelegramSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private TelegramChatRepository chatRepository;
    private TelegramClient telegramClient;
    private TelegramService service;
    private final List<TelegramChat> chats = new ArrayList<>();

    private BusinessRepository businessRepository;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(TelegramSettingsRepository.class);
        chatRepository = mock(TelegramChatRepository.class);
        businessRepository = mock(BusinessRepository.class);
        telegramClient = mock(TelegramClient.class);
        when(telegramClient.isConfigured()).thenReturn(true);
        when(telegramClient.botUsername()).thenReturn("TinhLukAlertsBot");
        when(businessRepository.findById(2L)).thenReturn(Optional.of(Business.builder().id(2L).name("Sota").build()));
        when(settingsRepository.save(any(TelegramSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // The chat repository behaves like a small table kept in `chats`.
        when(chatRepository.findAllByBusinessIdOrderByIdAsc(2L)).thenAnswer(invocation -> List.copyOf(chats));
        when(chatRepository.findAllByChatId(anyLong())).thenAnswer(invocation ->
                chats.stream().filter(chat -> chat.getChatId().equals(invocation.getArgument(0))).toList());
        when(chatRepository.findByIdAndBusinessId(anyLong(), any())).thenAnswer(invocation ->
                chats.stream().filter(chat -> chat.getId().equals(invocation.getArgument(0))).findFirst());
        when(chatRepository.save(any(TelegramChat.class))).thenAnswer(invocation -> {
            TelegramChat chat = invocation.getArgument(0);
            if (chat.getId() == null) {
                chat.setId((long) chats.size() + 1);
                chats.add(chat);
            }
            return chat;
        });
        service = new TelegramService(settingsRepository, chatRepository, businessRepository, telegramClient);
    }

    @Test
    void eachLinkAddsAChatAndTheSameChatLinkedAgainOnlyMovesTopic() {
        TelegramSettings settings = TelegramSettings.builder().businessId(2L).build();
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));

        var link = service.createLink(2L);
        when(settingsRepository.findByLinkCode(settings.getLinkCode())).thenReturn(Optional.of(settings));
        assertEquals("https://t.me/TinhLukAlertsBot?startgroup=" + settings.getLinkCode(), link.groupChatUrl());
        assertEquals(Optional.of("Sota"), service.linkChat(settings.getLinkCode(),
                new TelegramService.ChatPlace(-100777L, null, "Sota staff", "supergroup", "General")));
        assertNull(settings.getLinkCode());

        service.createLink(2L);
        when(settingsRepository.findByLinkCode(settings.getLinkCode())).thenReturn(Optional.of(settings));
        service.linkChat(settings.getLinkCode(), new TelegramService.ChatPlace(55L, null, "Vathana Sieng", "private", null));

        service.createLink(2L);
        when(settingsRepository.findByLinkCode(settings.getLinkCode())).thenReturn(Optional.of(settings));
        service.linkChat(settings.getLinkCode(), new TelegramService.ChatPlace(-100777L, 42L, "Sota staff", "supergroup", "Shop alerts"));

        TelegramStatusResponse status = service.status(2L);
        assertEquals(2, status.chats().size());
        assertEquals("Shop alerts", status.chats().get(0).topicTitle());
        assertTrue(status.chats().get(0).inTopic());
        assertEquals("private", status.chats().get(1).chatType());
        assertTrue(status.chats().get(1).allStores());
    }

    @Test
    void expiredLinkCodeDoesNotConnect() {
        TelegramSettings settings = TelegramSettings.builder()
                .businessId(2L).linkCode("old").linkCodeExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(settingsRepository.findByLinkCode("old")).thenReturn(Optional.of(settings));

        assertEquals(Optional.empty(), service.linkChat("old", new TelegramService.ChatPlace(55L, null, "Owner", "private", null)));
        assertTrue(chats.isEmpty());
    }

    @Test
    void alertsGoOnlyToChatsThatCoverTheStoreAndWantTheAlert() {
        TelegramChat staffGroup = chat(-100777L, 42L);
        staffGroup.useStores(List.of(10L));
        TelegramChat owner = chat(55L, null);
        owner.setKhqrAlerts(false);

        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, 10L, "paid at store 10"));
        assertFalse(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, 11L, "paid at store 11"));
        assertTrue(service.notify(2L, TelegramAlertType.LOW_STOCK, 11L, "low at store 11"));

        verify(telegramClient).sendMessage(-100777L, 42L, "paid at store 10");
        verify(telegramClient, never()).sendMessage(55L, null, "paid at store 10");
        verify(telegramClient, never()).sendMessage(-100777L, 42L, "low at store 11");
        verify(telegramClient).sendMessage(55L, null, "low at store 11");
    }

    @Test
    void formattedAlertsAreSentAsHtmlAndFallBackToPlainTextWhenRejected() {
        chat(55L, null);
        String html = "<b>Payment received · $1.50</b>\nCustomer: A &amp; B";

        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, 10L, html, "HTML"));
        verify(telegramClient).sendMessage(55L, null, html, "HTML");

        doThrow(new TelegramClient.TelegramException("Bad Request: can't parse entities", 400))
                .when(telegramClient).sendMessage(55L, null, html, "HTML");
        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, 10L, html, "HTML"));
        verify(telegramClient).sendMessage(55L, null, "Payment received · $1.50\nCustomer: A & B");
    }

    @Test
    void storesAndSwitchesAreChangedPerChat() {
        TelegramChat group = chat(-100777L, null);

        service.updateChat(2L, group.getId(), new UpdateTelegramChatRequest(false, List.of(12L, 10L, 10L), null, false, null, null));
        assertEquals(List.of(10L, 12L), group.storeIdList());
        assertFalse(group.getOrderAlerts());

        service.updateChat(2L, group.getId(), new UpdateTelegramChatRequest(true, null, null, null, null, null));
        assertNull(group.storeIdList());

        ResponseStatusException noStores = assertThrows(ResponseStatusException.class,
                () -> service.updateChat(2L, group.getId(), new UpdateTelegramChatRequest(false, List.of(), null, null, null, null)));
        assertEquals("Choose at least one store, or all stores", noStores.getReason());
    }

    @Test
    void disconnectingOneChatKeepsTheOthers() {
        TelegramChat group = chat(-100777L, null);
        chat(55L, null);
        org.mockito.Mockito.doAnswer(invocation -> chats.remove(invocation.<TelegramChat>getArgument(0)))
                .when(chatRepository).delete(any(TelegramChat.class));

        TelegramStatusResponse status = service.disconnectChat(2L, group.getId());

        assertEquals(1, status.chats().size());
        assertEquals(55L, chats.get(0).getChatId());
        verify(telegramClient).sendMessage(-100777L, null, "TinhLuk alerts for Sota are turned off here.");
    }

    @Test
    void hereMovesEveryLinkOfTheChatToTheTopic() {
        chat(-100777L, null);

        assertEquals(List.of("Sota"), service.useTopic(new TelegramService.ChatPlace(-100777L, 42L, null, null, "Shop alerts")));
        assertEquals(42L, chats.get(0).getMessageThreadId());
        assertEquals("Shop alerts", chats.get(0).getTopicTitle());
        assertEquals("Sota staff", chats.get(0).getChatTitle());
    }

    @Test
    void chatThatBlockedTheBotIsDisconnected() {
        chat(55L, null);
        doThrow(new TelegramClient.TelegramException("Forbidden: bot was blocked by the user", 403))
                .when(telegramClient).sendMessage(anyLong(), any(), anyString());

        assertFalse(service.notify(2L, TelegramAlertType.LOW_STOCK, null, "low"));
        verify(chatRepository).deleteAll(List.of(chats.get(0)));
    }

    @Test
    void closedTopicExplainsHowToPickAnother() {
        TelegramChat group = chat(-100777L, null);
        doThrow(new TelegramClient.TelegramException("Bad Request: TOPIC_CLOSED", 400))
                .when(telegramClient).sendMessage(anyLong(), any(), anyString());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.sendTest(2L, group.getId()));
        assertEquals(TelegramService.TOPIC_UNAVAILABLE, exception.getReason());
    }

    @Test
    void nothingIsSentWithoutABotToken() {
        chat(55L, null);
        when(telegramClient.isConfigured()).thenReturn(false);

        assertFalse(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, null, "paid"));
        verify(telegramClient, never()).sendMessage(anyLong(), any(), anyString());
    }

    private TelegramChat chat(long chatId, Long threadId) {
        return chatRepository.save(TelegramChat.builder()
                .businessId(2L).chatId(chatId).messageThreadId(threadId).chatTitle("Sota staff").build());
    }

    @Test
    void starterPlanGetsPaymentAlertsOnlyAndOneChat() {
        when(businessRepository.findById(2L)).thenReturn(Optional.of(Business.builder().id(2L).name("Sota").plan("STARTER").build()));
        chat(55L, null);

        assertTrue(service.notify(2L, TelegramAlertType.KHQR_PAYMENT, 10L, "paid"));
        assertFalse(service.notify(2L, TelegramAlertType.LOW_STOCK, 10L, "low"));
        assertFalse(service.notify(2L, TelegramAlertType.ONLINE_ORDER, 10L, "order"));
        verify(telegramClient, never()).sendMessage(55L, null, "low");

        var refused = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.createLink(2L));
        assertTrue(refused.getReason().contains("Starter plan allows 1 Telegram chat"));
    }
}
