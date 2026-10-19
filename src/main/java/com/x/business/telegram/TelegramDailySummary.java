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

/**
 * Sends each chat the day's sales of its stores once the business's summary hour has passed in its time zone, and
 * at the same hour last week's sales on Mondays and last month's on the 1st.
 */
@Component
@RequiredArgsConstructor
public class TelegramDailySummary {

    private static final Logger log = LoggerFactory.getLogger(TelegramDailySummary.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_START = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

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
        if (business == null || !com.x.business.plan.Plan.of(business.getPlan())
                .includes(com.x.business.plan.PlanFeature.ALL_TELEGRAM_ALERTS)) {
            return;
        }
        int summaryHour = settingsRepository.findById(business.getId()).map(TelegramSettings::getDailySummaryHour).orElse(21);
        ZoneId zone = zoneOf(business);
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate today = localNow.toLocalDate();
        if (localNow.getHour() < summaryHour) {
            return;
        }
        if (!today.equals(chat.getSummarySentOn())) {
            // Payments are stored in the server's local time, so the business's day is converted to it.
            String text = summaryText(business, chat.storeIdList(), today, serverTime(today, zone),
                    localNow.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
            if (telegramService.deliver(chat.getChatId(), chat.getMessageThreadId(), text)) {
                chatRepository.markSummarySent(chat.getId(), today);
            }
        }
        // On Monday, last week (Monday to Sunday); on the 1st, last month. Each is sent once, on that day only.
        if (today.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
            LocalDate weekStart = today.minusWeeks(1);
            if (!weekStart.equals(chat.getWeeklySummarySentOn())) {
                String text = summaryText(business, chat.storeIdList(), "📅 Weekly summary · ",
                        WEEK_START.format(weekStart) + " – " + DAY.format(today.minusDays(1)), "No sales last week.",
                        serverTime(weekStart, zone), serverTime(today, zone));
                if (telegramService.deliver(chat.getChatId(), chat.getMessageThreadId(), text)) {
                    chatRepository.markWeeklySummarySent(chat.getId(), weekStart);
                }
            }
        }
        if (today.getDayOfMonth() == 1) {
            LocalDate monthStart = today.minusMonths(1);
            if (!monthStart.equals(chat.getMonthlySummarySentOn())) {
                String text = summaryText(business, chat.storeIdList(), "🗓️ Monthly summary · ",
                        MONTH.format(monthStart), "No sales last month.",
                        serverTime(monthStart, zone), serverTime(today, zone));
                if (telegramService.deliver(chat.getChatId(), chat.getMessageThreadId(), text)) {
                    chatRepository.markMonthlySummarySent(chat.getId(), monthStart);
                }
            }
        }
    }

    /** Midnight at the start of the business-local day, in the server's time, which is how payments are stored. */
    private static LocalDateTime serverTime(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** The day's sales of the given stores, or of every store when stores is null. */
    String summaryText(Business business, List<Long> stores, LocalDate day, LocalDateTime from, LocalDateTime to) {
        return summaryText(business, stores, "📊 Daily summary · ", DAY.format(day), "No sales today.", from, to);
    }

    /** Sales of the given stores (every store when stores is null) between from and to, under a title and date line. */
    String summaryText(Business business, List<Long> stores, String title, String dateLine, String noSales,
                       LocalDateTime from, LocalDateTime to) {
        StringBuilder text = new StringBuilder(title).append(business.getName())
                .append('\n').append(dateLine).append('\n');
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
            return text.append('\n').append(noSales).toString();
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
