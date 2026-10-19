package com.x.business.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.business.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(telegramService.linkChat("CODE123", new TelegramService.ChatPlace(-100777L, null, "Sota staff", "group", null))).thenReturn(Optional.of("Sota"));

        poller.handle(update("""
                {"update_id":1,"message":{"text":"/start@TinhLukAlertsBot CODE123","chat":{"id":-100777,"title":"Sota staff","type":"group"}}}
                """));

        verify(telegramService).deliver(eq(-100777L), isNull(), contains("TinhLuk alerts are on for Sota"));
    }

    @Test
    void bareStartInAConnectedGroupTopicPointsToHere() throws Exception {
        when(telegramService.businessesUsing(-100777L)).thenReturn(List.of("Shopify"));

        poller.handle(update("""
                {"update_id":5,"message":{"text":"/start@tinhluk_bot","is_topic_message":true,"message_thread_id":42,
                 "chat":{"id":-100777,"title":"Staff","type":"supergroup","is_forum":true}}}
                """));

        verify(telegramService).deliver(eq(-100777L), eq(42L), contains("already gets TinhLuk alerts for Shopify"));
        verify(telegramService).deliver(eq(-100777L), eq(42L), contains("send /here"));
        verify(telegramService, never()).linkChat(anyString(), any());
    }

    @Test
    void bareStartInANewChatExplainsHowToConnect() throws Exception {
        when(telegramService.businessesUsing(55L)).thenReturn(List.of());

        poller.handle(update("""
                {"update_id":6,"message":{"text":"/start","chat":{"id":55,"first_name":"Vathana","type":"private"}}}
                """));

        verify(telegramService).deliver(eq(55L), isNull(), contains("tap Connect Telegram"));
    }

    @Test
    void expiredCodeExplainsHowToReconnect() throws Exception {
        when(telegramService.linkChat("OLD", new TelegramService.ChatPlace(55L, null, "Vathana Sieng", "private", null))).thenReturn(Optional.empty());

        poller.handle(update("""
                {"update_id":2,"message":{"text":"/start OLD","chat":{"id":55,"first_name":"Vathana","last_name":"Sieng","type":"private"}}}
                """));

        verify(telegramService).deliver(eq(55L), isNull(), contains("This link has expired"));
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

        verify(telegramService).deliver(eq(55L), isNull(), contains("turned off"));
        verify(telegramService).forgetChat(-100777L);
    }

    @Test
    void hereInATopicSendsTheAlertsToThatTopic() throws Exception {
        when(telegramService.useTopic(any())).thenReturn(List.of("Sota"));

        poller.handle(update("""
                {"update_id":6,"message":{"text":"/here@TinhLukAlertsBot","message_thread_id":42,"is_topic_message":true,
                 "reply_to_message":{"message_id":42,"forum_topic_created":{"name":"Shop alerts"}},
                 "chat":{"id":-100777,"title":"Sota staff","type":"supergroup","is_forum":true}}}
                """));

        verify(telegramService).useTopic(new TelegramService.ChatPlace(-100777L, 42L, "Sota staff", "supergroup", "Shop alerts"));
        verify(telegramService).deliver(eq(-100777L), eq(42L), contains("will arrive in this topic"));
    }

    @Test
    void startFromATopicConnectsThatTopic() throws Exception {
        when(telegramService.linkChat("CODE123", new TelegramService.ChatPlace(-100777L, 42L, "Sota staff", "supergroup", null))).thenReturn(Optional.of("Sota"));

        poller.handle(update("""
                {"update_id":7,"message":{"text":"/start@TinhLukAlertsBot CODE123","message_thread_id":42,"is_topic_message":true,
                 "chat":{"id":-100777,"title":"Sota staff","type":"supergroup","is_forum":true}}}
                """));

        verify(telegramService).deliver(eq(-100777L), eq(42L), contains("TinhLuk alerts are on for Sota"));
    }

    @Test
    void hereInAChatThatIsNotConnectedExplainsHowToConnect() throws Exception {
        when(telegramService.useTopic(any())).thenReturn(List.of());

        poller.handle(update("""
                {"update_id":8,"message":{"text":"/here","chat":{"id":55,"type":"private"}}}
                """));

        verify(telegramService).deliver(eq(55L), isNull(), contains("Connect Telegram"));
    }

    @Test
    void ordinaryMessagesAreIgnored() throws Exception {
        poller.handle(update("""
                {"update_id":5,"message":{"text":"hello","chat":{"id":55,"type":"private"}}}
                """));

        verify(telegramService, never()).deliver(anyLong(), any(), anyString());
    }

    private JsonNode update(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
