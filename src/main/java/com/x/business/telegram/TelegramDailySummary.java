package com.x.business.telegram;

import com.x.business.entity.Business;
import com.x.business.entity.TelegramChat;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramChatRepository;
import com.x.business.repository.TelegramSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Sends each chat the day's sales of its stores once the business's summary hour has passed in its time zone. */
@Component
@RequiredArgsConstructor
public class TelegramDailySummary {

    private static final Logger log = LoggerFactory.getLogger(TelegramDailySummary.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final TelegramSettingsRepository settingsRepository;
    private final TelegramChatRepository chatRepository;
    private final BusinessRepository businessRepository;
    private final SalesReportClient salesReportClient;
    private final TelegramService telegramService;
    private final TelegramClient telegramClient;

    @Scheduled(cron = "0 */5 * * * *")
    public void sendDueSummaries() {
        if (!telegramClient.isConfigured()) {
            return;
        }
        Instant now = Instant.now();
        for (TelegramChat chat : chatRepository.findAllByDailySummaryTrue()) {
            try {
                sendIfDue(chat, now);
            } catch (RuntimeException exception) {
                log.warn("Daily summary for business {} chat {} failed: {}", chat.getBusinessId(), chat.getId(), exception.getMessage());
            }
        }
    }

    void sendIfDue(TelegramChat chat, Instant now) {
        Business business = businessRepository.findById(chat.getBusinessId()).orElse(null);
        if (business == null) {
            return;
        }
        int summaryHour = settingsRepository.findById(business.getId()).map(TelegramSettings::getDailySummaryHour).orElse(21);
        ZoneId zone = zoneOf(business);
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate today = localNow.toLocalDate();
        if (localNow.getHour() < summaryHour || today.equals(chat.getSummarySentOn())) {
            return;
        }
        // Payments are stored in the server's local time, so the business's day is converted to it.
        LocalDateTime from = today.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime to = localNow.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        String text = summaryText(business, chat.storeIdList(), today, from, to);
        if (telegramService.deliver(chat.getChatId(), chat.getMessageThreadId(), text)) {
            chatRepository.markSummarySent(chat.getId(), today);
        }
    }

    /** The day's sales of the given stores, or of every store when stores is null. */
    String summaryText(Business business, List<Long> stores, LocalDate day, LocalDateTime from, LocalDateTime to) {
        StringBuilder text = new StringBuilder("📊 Daily summary · ").append(business.getName())
                .append('\n').append(DAY.format(day)).append('\n');
        Map<String, BigDecimal> netByCurrency = new LinkedHashMap<>();
        for (SalesReportClient.StoreName store : salesReportClient.stores(business.getId())) {
            if (stores != null && !stores.contains(store.id())) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            for (String currency : currencies(business)) {
                List<SalesReportClient.PaymentTotal> totals = salesReportClient.paymentTotals(store.id(), currency, from, to);
                if (totals.isEmpty()) {
                    continue;
                }
                BigDecimal sales = totals.stream().map(SalesReportClient.PaymentTotal::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal refunds = totals.stream().map(SalesReportClient.PaymentTotal::refundedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                long payments = totals.stream().mapToLong(SalesReportClient.PaymentTotal::paymentCount).sum();
                lines.add("• Sales: " + money(sales, currency) + " from " + payments + (payments == 1 ? " payment" : " payments"));
                lines.add("• " + totals.stream()
                        .map(total -> methodLabel(total) + " " + money(total.totalAmount(), currency))
                        .collect(Collectors.joining(" · ")));
                if (refunds.signum() > 0) {
                    lines.add("• Refunds: " + money(refunds, currency));
                }
                netByCurrency.merge(currency, sales.subtract(refunds), BigDecimal::add);
            }
            if (!lines.isEmpty()) {
                text.append('\n').append(store.name()).append('\n').append(String.join("\n", lines)).append('\n');
            }
        }
        if (netByCurrency.isEmpty()) {
            return text.append("\nNo sales today.").toString();
        }
        return text.append("\nTotal after refunds: ")
                .append(netByCurrency.entrySet().stream()
                        .map(entry -> money(entry.getValue(), entry.getKey()))
                        .collect(Collectors.joining(" + ")))
                .toString();
    }

    /** The business currency first, then the other of USD and riel, since a shop can sell in both. */
    private static List<String> currencies(Business business) {
        String primary = business.getDefaultCurrencyCode() == null ? "USD" : business.getDefaultCurrencyCode();
        return "KHR".equals(primary) ? List.of("KHR", "USD") : List.of(primary, "KHR");
    }

    private static String methodLabel(SalesReportClient.PaymentTotal total) {
        return switch (total.method()) {
            case "CASH" -> "Cash";
            case "CARD" -> "Card";
            case "QR" -> "SIMULATED".equals(total.provider()) ? "Demo QR" : "KHQR";
            default -> total.method();
        };
    }

    static String money(BigDecimal amount, String currency) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        if ("KHR".equals(currency)) {
            return "៛ " + new DecimalFormat("#,##0", symbols).format(amount.setScale(0, RoundingMode.HALF_UP));
        }
        String formatted = new DecimalFormat("#,##0.00", symbols).format(amount.setScale(2, RoundingMode.HALF_UP));
        return "USD".equals(currency) ? "$" + formatted : formatted + " " + currency;
    }

    private static ZoneId zoneOf(Business business) {
        try {
            return business.getTimeZone() == null ? DEFAULT_ZONE : ZoneId.of(business.getTimeZone());
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }
}
