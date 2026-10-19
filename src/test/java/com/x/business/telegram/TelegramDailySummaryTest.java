package com.x.business.telegram;

import com.x.business.entity.Business;
import com.x.business.entity.TelegramChat;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramChatRepository;
import com.x.business.repository.TelegramSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramDailySummaryTest {

    private TelegramChatRepository chatRepository;
    private SalesReportClient salesReportClient;
    private TelegramService telegramService;
    private TelegramDailySummary summary;
    private final Business business = Business.builder()
            .id(2L).name("Sota").defaultCurrencyCode("USD").timeZone("Asia/Phnom_Penh").build();

    @BeforeEach
    void setUp() {
        chatRepository = mock(TelegramChatRepository.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);
        salesReportClient = mock(SalesReportClient.class);
        telegramService = mock(TelegramService.class);
        when(businessRepository.findById(2L)).thenReturn(Optional.of(business));
        summary = new TelegramDailySummary(
                mock(TelegramSettingsRepository.class), chatRepository, businessRepository, salesReportClient, telegramService,
                mock(TelegramClient.class));
    }

    @Test
    void summaryListsEachStoreAndTheNetTotal() {
        when(salesReportClient.stores(2L)).thenReturn(List.of(new SalesReportClient.StoreName(2L, "Shopify")));
        when(salesReportClient.paymentTotals(eq(2L), eq("USD"), any(), any())).thenReturn(List.of(
                new SalesReportClient.PaymentTotal("CASH", "NONE", 25, new BigDecimal("200.00"), new BigDecimal("5.00")),
                new SalesReportClient.PaymentTotal("QR", "BAKONG", 17, new BigDecimal("150.00"), BigDecimal.ZERO)));
        when(salesReportClient.paymentTotals(eq(2L), eq("KHR"), any(), any())).thenReturn(List.of(
                new SalesReportClient.PaymentTotal("CASH", "NONE", 2, new BigDecimal("41000"), BigDecimal.ZERO)));

        String text = summary.summaryText(business, null, LocalDate.of(2026, 10, 18), LocalDateTime.now(), LocalDateTime.now());

        assertEquals("""
                📊 Daily summary · Sota
                Sun 18 Oct 2026

                Shopify
                • Sales: $350.00 from 42 payments
                • Cash $200.00 · KHQR $150.00
                • Refunds: $5.00
                • Sales: ៛ 41,000 from 2 payments
                • Cash ៛ 41,000

                Total after refunds: $345.00 + ៛ 41,000""", text);
    }

    @Test
    void daysWithoutPaymentsSayNoSales() {
        when(salesReportClient.stores(2L)).thenReturn(List.of(new SalesReportClient.StoreName(2L, "Shopify")));

        String text = summary.summaryText(business, null, LocalDate.of(2026, 10, 18), LocalDateTime.now(), LocalDateTime.now());

        assertEquals("📊 Daily summary · Sota\nSun 18 Oct 2026\n\nNo sales today.", text);
    }

    @Test
    void sendsOnceAfterTheSummaryHourInTheBusinessTimeZone() {
        TelegramChat chat = TelegramChat.builder().id(9L).businessId(2L).chatId(55L).build();
        when(telegramService.deliver(eq(55L), any(), anyString())).thenReturn(true);

        summary.sendIfDue(chat, Instant.parse("2026-10-18T13:59:00Z"));   // 20:59 in Phnom Penh
        verify(telegramService, never()).deliver(anyLong(), any(), anyString());

        summary.sendIfDue(chat, Instant.parse("2026-10-18T14:00:00Z"));   // 21:00 in Phnom Penh
        verify(chatRepository).markSummarySent(9L, LocalDate.of(2026, 10, 18));

        chat.setSummarySentOn(LocalDate.of(2026, 10, 18));
        summary.sendIfDue(chat, Instant.parse("2026-10-18T15:00:00Z"));
        verify(telegramService).deliver(eq(55L), any(), anyString());
    }

    @Test
    void aChatForSomeStoresGetsOnlyThoseStores() {
        when(salesReportClient.stores(2L)).thenReturn(List.of(
                new SalesReportClient.StoreName(2L, "Shopify"), new SalesReportClient.StoreName(3L, "Riverside")));
        when(salesReportClient.paymentTotals(eq(3L), eq("USD"), any(), any())).thenReturn(List.of(
                new SalesReportClient.PaymentTotal("CASH", "NONE", 1, new BigDecimal("4.00"), BigDecimal.ZERO)));

        String text = summary.summaryText(business, List.of(3L), LocalDate.of(2026, 10, 18), LocalDateTime.now(), LocalDateTime.now());

        assertEquals("""
                📊 Daily summary · Sota
                Sun 18 Oct 2026

                Riverside
                • Sales: $4.00 from 1 payment
                • Cash $4.00

                Total after refunds: $4.00""", text);
        verify(salesReportClient, never()).paymentTotals(eq(2L), any(), any(), any());
    }
}
