package com.x.business.telegram;

import com.x.business.dto.TelegramChatResponse;
import com.x.business.dto.TelegramLinkResponse;
import com.x.business.dto.TelegramStatusResponse;
import com.x.business.dto.UpdateTelegramChatRequest;
import com.x.business.dto.UpdateTelegramPreferencesRequest;
import com.x.business.entity.Business;
import com.x.business.entity.TelegramChat;
import com.x.business.entity.TelegramSettings;
import com.x.business.repository.BusinessRepository;
import com.x.business.repository.TelegramChatRepository;
import com.x.business.repository.TelegramSettingsRepository;
import com.x.business.realtime.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Connects a business to Telegram chats and sends each chat the alerts of its stores. */
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String TOPIC_UNAVAILABLE = "The Telegram topic for alerts is closed. In the group, open the topic that should get alerts and send /here.";

    private final TelegramSettingsRepository settingsRepository;
    private final TelegramChatRepository chatRepository;
    private final BusinessRepository businessRepository;
    private final TelegramClient telegramClient;
    private final RealtimePublisher realtime;

    @Transactional(readOnly = true)
    public TelegramStatusResponse status(Long businessId) {
        requireBusiness(businessId);
        return toStatus(settingsFor(businessId), chatRepository.findAllByBusinessIdOrderByIdAsc(businessId));
    }

    /** Creates one-time t.me links; a chat is added when someone presses Start with one of them. */
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
        requireChatAllowance(businessId);
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
        if (request.lowStockThreshold() != null) {
            settings.setLowStockThreshold(request.lowStockThreshold());
        }
        if (request.dailySummaryHour() != null) {
            settings.setDailySummaryHour(request.dailySummaryHour());
        }
        return toStatus(settingsRepository.save(settings), chatRepository.findAllByBusinessIdOrderByIdAsc(businessId));
    }

    /** Changes which stores and which alerts one chat gets. */
    @Transactional
    public TelegramStatusResponse updateChat(Long businessId, Long chatRowId, UpdateTelegramChatRequest request) {
        requireBusiness(businessId);
        TelegramChat chat = requireChat(businessId, chatRowId);
        if (Boolean.TRUE.equals(request.allStores())) {
            chat.useStores(null);
        } else if (Boolean.FALSE.equals(request.allStores()) || request.storeIds() != null) {
            if (request.storeIds() == null || request.storeIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one store, or all stores");
            }
            chat.useStores(request.storeIds());
        }
        if (request.khqrAlerts() != null) {
            chat.setKhqrAlerts(request.khqrAlerts());
        }
        if (request.orderAlerts() != null) {
            chat.setOrderAlerts(request.orderAlerts());
        }
        if (request.lowStockAlerts() != null) {
            chat.setLowStockAlerts(request.lowStockAlerts());
        }
        if (request.dailySummary() != null) {
            chat.setDailySummary(request.dailySummary());
        }
        chatRepository.save(chat);
        return status(businessId);
    }

    /** Stops alerts to one chat and tells the chat. */
    @Transactional
    public TelegramStatusResponse disconnectChat(Long businessId, Long chatRowId) {
        String businessName = requireBusiness(businessId).getName();
        TelegramChat chat = requireChat(businessId, chatRowId);
        chatRepository.delete(chat);
        deliver(chat.getChatId(), chat.getMessageThreadId(), "TinhLuk alerts for " + businessName + " are turned off here.");
        return status(businessId);
    }

    /** Stops alerts to every chat of the business. */
    @Transactional
    public TelegramStatusResponse disconnect(Long businessId) {
        String businessName = requireBusiness(businessId).getName();
        TelegramSettings settings = settingsFor(businessId);
        settings.setLinkCode(null);
        settings.setLinkCodeExpiresAt(null);
        settingsRepository.save(settings);
        List<TelegramChat> chats = chatRepository.findAllByBusinessIdOrderByIdAsc(businessId);
        chatRepository.deleteAll(chats);
        chats.forEach(chat -> deliver(chat.getChatId(), chat.getMessageThreadId(), "TinhLuk alerts for " + businessName + " are turned off here."));
        return toStatus(settings, List.of());
    }

    /** Sends a test message to one chat; Telegram's reason is reported when it refuses. */
    public void sendTest(Long businessId, Long chatRowId) {
        String businessName = requireBusiness(businessId).getName();
        sendTest(requireChat(businessId, chatRowId), businessName);
    }

    /** Sends a test message to every chat of the business. */
    public void sendTest(Long businessId) {
        String businessName = requireBusiness(businessId).getName();
        List<TelegramChat> chats = chatRepository.findAllByBusinessIdOrderByIdAsc(businessId);
        if (chats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect a Telegram chat first");
        }
        chats.forEach(chat -> sendTest(chat, businessName));
    }

    private void sendTest(TelegramChat chat, String businessName) {
        try {
            telegramClient.sendMessage(chat.getChatId(), chat.getMessageThreadId(),
                    "✅ Test alert from TinhLuk for " + businessName + ". Alerts will arrive here.");
        } catch (TelegramClient.TelegramException exception) {
            if (exception.chatGone()) {
                forgetChat(chat.getChatId());
            }
            if (exception.topicUnavailable()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, TOPIC_UNAVAILABLE);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Telegram did not accept the message: " + exception.getMessage());
        }
    }

    /** Sends an alert from another service to each chat that wants this kind of alert for this store. */
    public boolean notify(Long businessId, TelegramAlertType type, Long storeId, String text) {
        return notify(businessId, type, storeId, text, null);
    }

    /** As above; parseMode "HTML" sends the text with Telegram formatting. */
    public boolean notify(Long businessId, TelegramAlertType type, Long storeId, String text, String parseMode) {
        if (!telegramClient.isConfigured() || !planAllows(businessId, type)) {
            return false;
        }
        boolean sent = false;
        for (TelegramChat chat : chatRepository.findAllByBusinessIdOrderByIdAsc(businessId)) {
            if (wants(chat, type) && chat.covers(storeId)) {
                sent |= deliver(chat.getChatId(), chat.getMessageThreadId(), text, parseMode);
            }
        }
        return sent;
    }

    /**
     * Adds the chat whose /start carried this code, in the topic it was sent from if the group has topics.
     * The same chat linked again keeps its stores and alerts and only moves to the new topic.
     * Returns the business name, or empty if the code is unknown or expired.
     */
    @Transactional
    public Optional<String> linkChat(String code, ChatPlace place) {
        TelegramSettings settings = settingsRepository.findByLinkCode(code).orElse(null);
        if (settings == null
                || settings.getLinkCodeExpiresAt() == null
                || settings.getLinkCodeExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        Long businessId = settings.getBusinessId();
        TelegramChat chat = chatRepository.findAllByChatId(place.chatId()).stream()
                .filter(existing -> businessId.equals(existing.getBusinessId()))
                .findFirst()
                .orElseGet(() -> TelegramChat.builder().businessId(businessId).chatId(place.chatId()).build());
        place.applyTo(chat);
        chatRepository.save(chat);
        settings.setLinkCode(null);
        settings.setLinkCodeExpiresAt(null);
        settingsRepository.save(settings);
        // The Telegram alerts page that showed the link updates on its own.
        realtime.toBusiness(businessId, "telegram.chats.changed", java.util.Map.of("linked", true));
        return businessRepository.findById(businessId).map(Business::getName);
    }

    /**
     * Sends this chat's alerts to the topic /here was sent from, or to the main chat for the General topic;
     * returns the names of the businesses that use the chat.
     */
    @Transactional
    public List<String> useTopic(ChatPlace place) {
        List<TelegramChat> linked = chatRepository.findAllByChatId(place.chatId());
        linked.forEach(place::applyTo);
        chatRepository.saveAll(linked);
        return linked.stream()
                .map(chat -> businessRepository.findById(chat.getBusinessId()).map(Business::getName).orElse("your business"))
                .distinct()
                .toList();
    }

    /** Names of the businesses already sending alerts to this chat; empty if it is not connected. */
    @Transactional(readOnly = true)
    public List<String> businessesUsing(long chatId) {
        return chatRepository.findAllByChatId(chatId).stream()
                .map(chat -> businessRepository.findById(chat.getBusinessId()).map(Business::getName).orElse("your business"))
                .distinct()
                .toList();
    }

    /** Stops alerts to a chat that sent /stop or removed the bot; returns how many businesses used it. */
    @Transactional
    public int forgetChat(long chatId) {
        List<TelegramChat> linked = chatRepository.findAllByChatId(chatId);
        chatRepository.deleteAll(linked);
        linked.stream().map(TelegramChat::getBusinessId).distinct()
                .forEach(businessId -> realtime.toBusiness(businessId, "telegram.chats.changed", java.util.Map.of("linked", false)));
        return linked.size();
    }

    boolean deliver(long chatId, String text) {
        return deliver(chatId, null, text);
    }

    /** Sends a message, to a topic when threadId is set; a chat that blocked or removed the bot is disconnected. */
    boolean deliver(long chatId, Long threadId, String text) {
        return deliver(chatId, threadId, text, null);
    }

    /** Formatted text Telegram cannot read (error 400) is sent again as plain text, so the alert still arrives. */
    boolean deliver(long chatId, Long threadId, String text, String parseMode) {
        try {
            if (parseMode == null) {
                telegramClient.sendMessage(chatId, threadId, text);
            } else {
                try {
                    telegramClient.sendMessage(chatId, threadId, text, parseMode);
                } catch (TelegramClient.TelegramException exception) {
                    if (exception.getErrorCode() != 400 || exception.topicUnavailable()) throw exception;
                    log.warn("Telegram rejected formatted alert for chat {}; sending plain text: {}", chatId, exception.getMessage());
                    telegramClient.sendMessage(chatId, threadId, HtmlUtils.htmlUnescape(text.replaceAll("<[^>]+>", "")));
                }
            }
            return true;
        } catch (TelegramClient.TelegramException exception) {
            log.warn("Telegram message to chat {} topic {} failed: {}", chatId, threadId, exception.getMessage());
            if (exception.chatGone()) {
                forgetChat(chatId);
            }
            return false;
        }
    }

/** Payment and account alerts are in every plan; order, low-stock and daily-summary alerts need Business or Pro. */
    boolean planAllows(Long businessId, TelegramAlertType type) {
        if (type == TelegramAlertType.KHQR_PAYMENT || type == TelegramAlertType.ACCOUNT) return true;
        return businessRepository.findById(businessId)
                .map(business -> com.x.business.plan.Plan.of(business.getPlan())
                        .includes(com.x.business.plan.PlanFeature.ALL_TELEGRAM_ALERTS))
                .orElse(false);
    }

    /** Each plan allows a number of connected chats; a new link is refused once the plan's chats are in use. */
    private void requireChatAllowance(Long businessId) {
        com.x.business.plan.Plan plan = businessRepository.findById(businessId)
                .map(business -> com.x.business.plan.Plan.of(business.getPlan()))
                .orElse(com.x.business.plan.Plan.LEGACY_DEFAULT);
        Integer limit = plan.maxTelegramChats();
        if (limit != null && chatRepository.findAllByBusinessIdOrderByIdAsc(businessId).size() >= limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your " + plan.toResponse().name() + " plan allows "
                    + limit + (limit == 1 ? " Telegram chat" : " Telegram chats")
                    + ". Disconnect a chat or ask TinhLuk to upgrade your plan.");
        }
    }

    static boolean wants(TelegramChat chat, TelegramAlertType type) {
        return switch (type) {
            case KHQR_PAYMENT -> Boolean.TRUE.equals(chat.getKhqrAlerts());
            case ONLINE_ORDER -> Boolean.TRUE.equals(chat.getOrderAlerts());
            case LOW_STOCK -> Boolean.TRUE.equals(chat.getLowStockAlerts());
            case DAILY_SUMMARY -> Boolean.TRUE.equals(chat.getDailySummary());
            case ACCOUNT -> true;
        };
    }

    private TelegramStatusResponse toStatus(TelegramSettings settings, List<TelegramChat> chats) {
        boolean configured = telegramClient.isConfigured();
        return new TelegramStatusResponse(
                configured,
                configured ? telegramClient.botUsername() : null,
                !chats.isEmpty(),
                chats.isEmpty() ? null : chats.get(0).getChatTitle(),
                any(chats, TelegramChat::getKhqrAlerts),
                any(chats, TelegramChat::getOrderAlerts),
                any(chats, TelegramChat::getLowStockAlerts),
                any(chats, TelegramChat::getDailySummary),
                settings.getLowStockThreshold(),
                settings.getDailySummaryHour(),
                settings.getLinkCode() != null
                        && settings.getLinkCodeExpiresAt() != null
                        && settings.getLinkCodeExpiresAt().isAfter(LocalDateTime.now()),
                chats.stream().map(TelegramService::toResponse).toList());
    }

    private static boolean any(List<TelegramChat> chats, Function<TelegramChat, Boolean> flag) {
        return chats.stream().anyMatch(chat -> Boolean.TRUE.equals(flag.apply(chat)));
    }

    private static TelegramChatResponse toResponse(TelegramChat chat) {
        List<Long> stores = chat.storeIdList();
        return new TelegramChatResponse(
                chat.getId(),
                chat.getChatTitle(),
                chat.getChatType(),
                chat.getTopicTitle(),
                chat.getMessageThreadId() != null,
                stores == null,
                stores == null ? List.of() : stores,
                Boolean.TRUE.equals(chat.getKhqrAlerts()),
                Boolean.TRUE.equals(chat.getOrderAlerts()),
                Boolean.TRUE.equals(chat.getLowStockAlerts()),
                Boolean.TRUE.equals(chat.getDailySummary()),
                chat.getCreatedAt());
    }

    private TelegramSettings settingsFor(Long businessId) {
        return settingsRepository.findById(businessId)
                .orElseGet(() -> TelegramSettings.builder().businessId(businessId).build());
    }

    private TelegramChat requireChat(Long businessId, Long chatRowId) {
        return chatRepository.findByIdAndBusinessId(chatRowId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telegram chat not found"));
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

    /** Where a command came from: the chat, its topic if any, and their names as Telegram shows them. */
    public record ChatPlace(long chatId, Long threadId, String chatTitle, String chatType, String topicTitle) {

        void applyTo(TelegramChat chat) {
            chat.setMessageThreadId(threadId);
            chat.setTopicTitle(truncate(topicTitle, 128));
            if (chatTitle != null) {
                chat.setChatTitle(truncate(chatTitle, 255));
            }
            if (chatType != null) {
                chat.setChatType(chatType);
            }
        }

        private static String truncate(String value, int length) {
            return value == null || value.length() <= length ? value : value.substring(0, length);
        }
    }
}
