package com.x.business.telegram;

import com.x.business.dto.TelegramLinkResponse;
import com.x.business.dto.TelegramStatusResponse;
import com.x.business.dto.UpdateTelegramPreferencesRequest;
import com.x.business.entity.Business;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Connects a business to a Telegram chat and sends its alerts there. */
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramSettingsRepository settingsRepository;
    private final BusinessRepository businessRepository;
    private final TelegramClient telegramClient;

    @Transactional(readOnly = true)
    public TelegramStatusResponse status(Long businessId) {
        requireBusiness(businessId);
        return toStatus(settingsRepository.findById(businessId)
                .orElseGet(() -> TelegramSettings.builder().businessId(businessId).build()));
    }

    /** Creates one-time t.me links; the chat is connected when someone presses Start with one of them. */
    @Transactional
    public TelegramLinkResponse createLink(Long businessId) {
        requireBusiness(businessId);
        if (!telegramClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram alerts are not set up on the server yet");
        }
        String botUsername = telegramClient.botUsername();
        if (botUsername == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram cannot be reached right now. Try again in a moment.");
        }
        TelegramSettings settings = settingsFor(businessId);
        String code = newLinkCode();
        LocalDateTime expiresAt = LocalDateTime.now().plus(LINK_LIFETIME);
        settings.setLinkCode(code);
        settings.setLinkCodeExpiresAt(expiresAt);
        settingsRepository.save(settings);
        String botUrl = "https://t.me/" + botUsername;
        return new TelegramLinkResponse(botUrl + "?start=" + code, botUrl + "?startgroup=" + code, expiresAt);
    }

    @Transactional
    public TelegramStatusResponse updatePreferences(Long businessId, UpdateTelegramPreferencesRequest request) {
        requireBusiness(businessId);
        TelegramSettings settings = settingsFor(businessId);
        if (request.khqrAlerts() != null) {
            settings.setKhqrAlerts(request.khqrAlerts());
        }
        if (request.orderAlerts() != null) {
            settings.setOrderAlerts(request.orderAlerts());
        }
        if (request.lowStockAlerts() != null) {
            settings.setLowStockAlerts(request.lowStockAlerts());
        }
        if (request.dailySummary() != null) {
            settings.setDailySummary(request.dailySummary());
        }
        if (request.lowStockThreshold() != null) {
            settings.setLowStockThreshold(request.lowStockThreshold());
        }
        if (request.dailySummaryHour() != null) {
            settings.setDailySummaryHour(request.dailySummaryHour());
        }
        return toStatus(settingsRepository.save(settings));
    }

    @Transactional
    public TelegramStatusResponse disconnect(Long businessId) {
        String businessName = requireBusiness(businessId).getName();
        TelegramSettings settings = settingsFor(businessId);
        Long chatId = settings.getChatId();
        settings.setChatId(null);
        settings.setChatTitle(null);
        settings.setLinkCode(null);
        settings.setLinkCodeExpiresAt(null);
        TelegramSettings saved = settingsRepository.save(settings);
        if (chatId != null) {
            deliver(chatId, "TinhLok alerts for " + businessName + " are turned off for this chat.");
        }
        return toStatus(saved);
    }

    public void sendTest(Long businessId) {
        String businessName = requireBusiness(businessId).getName();
        Long chatId = settingsRepository.findById(businessId).map(TelegramSettings::getChatId).orElse(null);
        if (chatId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect a Telegram chat first");
        }
        try {
            telegramClient.sendMessage(chatId, "✅ Test alert from TinhLok for " + businessName + ". Alerts will arrive in this chat.");
        } catch (TelegramClient.TelegramException exception) {
            if (exception.chatGone()) {
                forgetChat(chatId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Telegram did not accept the message: " + exception.getMessage());
        }
    }

    /** Sends an alert from another service if the chat is connected and wants this kind of alert. */
    public boolean notify(Long businessId, TelegramAlertType type, String text) {
        if (!telegramClient.isConfigured()) {
            return false;
        }
        TelegramSettings settings = settingsRepository.findById(businessId).orElse(null);
        if (settings == null || settings.getChatId() == null || !wants(settings, type)) {
            return false;
        }
        return deliver(settings.getChatId(), text);
    }

    /** Connects the chat whose /start carried this code; returns the business name, or empty if the code is unknown or expired. */
    @Transactional
    public Optional<String> linkChat(String code, long chatId, String chatTitle) {
        TelegramSettings settings = settingsRepository.findByLinkCode(code).orElse(null);
        if (settings == null
                || settings.getLinkCodeExpiresAt() == null
                || settings.getLinkCodeExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        settings.setChatId(chatId);
        settings.setChatTitle(chatTitle == null || chatTitle.length() <= 255 ? chatTitle : chatTitle.substring(0, 255));
        settings.setLinkCode(null);
        settings.setLinkCodeExpiresAt(null);
        settingsRepository.save(settings);
        return businessRepository.findById(settings.getBusinessId()).map(Business::getName);
    }

    /** Stops alerts to a chat that sent /stop or removed the bot; returns how many businesses used it. */
    @Transactional
    public int forgetChat(long chatId) {
        List<TelegramSettings> linked = settingsRepository.findAllByChatId(chatId);
        for (TelegramSettings settings : linked) {
            settings.setChatId(null);
            settings.setChatTitle(null);
        }
        settingsRepository.saveAll(linked);
        return linked.size();
    }

    /** Sends a message; a chat that blocked or removed the bot is disconnected. */
    boolean deliver(long chatId, String text) {
        try {
            telegramClient.sendMessage(chatId, text);
            return true;
        } catch (TelegramClient.TelegramException exception) {
            log.warn("Telegram message to chat {} failed: {}", chatId, exception.getMessage());
            if (exception.chatGone()) {
                forgetChat(chatId);
            }
            return false;
        }
    }

    private boolean wants(TelegramSettings settings, TelegramAlertType type) {
        return switch (type) {
            case KHQR_PAYMENT -> Boolean.TRUE.equals(settings.getKhqrAlerts());
            case ONLINE_ORDER -> Boolean.TRUE.equals(settings.getOrderAlerts());
            case LOW_STOCK -> Boolean.TRUE.equals(settings.getLowStockAlerts());
            case DAILY_SUMMARY -> Boolean.TRUE.equals(settings.getDailySummary());
        };
    }

    private TelegramStatusResponse toStatus(TelegramSettings settings) {
        boolean configured = telegramClient.isConfigured();
        return new TelegramStatusResponse(
                configured,
                configured ? telegramClient.botUsername() : null,
                settings.getChatId() != null,
                settings.getChatTitle(),
                Boolean.TRUE.equals(settings.getKhqrAlerts()),
                Boolean.TRUE.equals(settings.getOrderAlerts()),
                Boolean.TRUE.equals(settings.getLowStockAlerts()),
                Boolean.TRUE.equals(settings.getDailySummary()),
                settings.getLowStockThreshold(),
                settings.getDailySummaryHour());
    }

    private TelegramSettings settingsFor(Long businessId) {
        return settingsRepository.findById(businessId)
                .orElseGet(() -> TelegramSettings.builder().businessId(businessId).build());
    }

    private Business requireBusiness(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    private static String newLinkCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
