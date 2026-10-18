package com.x.business.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.business.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramUpdatePollerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TelegramService telegramService;
    private TelegramUpdatePoller poller;

    @BeforeEach
    void setUp() {
        telegramService = mock(TelegramService.class);
        poller = new TelegramUpdatePoller(new TelegramProperties(), mock(TelegramClient.class), telegramService);
    }

    @Test
    void groupStartWithCodeConnectsTheGroup() throws Exception {
        when(telegramService.linkChat("CODE123", -100777L, "Sota staff")).thenReturn(Optional.of("Sota"));

        poller.handle(update("""
                {"update_id":1,"message":{"text":"/start@TinhLokAlertsBot CODE123","chat":{"id":-100777,"title":"Sota staff","type":"group"}}}
                """));

        verify(telegramService).deliver(eq(-100777L), contains("TinhLok alerts are on for Sota"));
    }

    @Test
    void expiredCodeExplainsHowToReconnect() throws Exception {
        when(telegramService.linkChat("OLD", 55L, "Vathana Sieng")).thenReturn(Optional.empty());

        poller.handle(update("""
                {"update_id":2,"message":{"text":"/start OLD","chat":{"id":55,"first_name":"Vathana","last_name":"Sieng","type":"private"}}}
                """));

        verify(telegramService).deliver(eq(55L), contains("This link has expired"));
    }

    @Test
    void stopAndRemovingTheBotDisconnectTheChat() throws Exception {
        when(telegramService.forgetChat(55L)).thenReturn(1);

        poller.handle(update("""
                {"update_id":3,"message":{"text":"/stop","chat":{"id":55,"type":"private"}}}
                """));
        poller.handle(update("""
                {"update_id":4,"my_chat_member":{"chat":{"id":-100777},"new_chat_member":{"status":"kicked"}}}
                """));

        verify(telegramService).deliver(eq(55L), contains("turned off"));
        verify(telegramService).forgetChat(-100777L);
    }

    @Test
    void ordinaryMessagesAreIgnored() throws Exception {
        poller.handle(update("""
                {"update_id":5,"message":{"text":"hello","chat":{"id":55,"type":"private"}}}
                """));

        verify(telegramService, never()).deliver(anyLong(), anyString());
    }

    private JsonNode update(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
