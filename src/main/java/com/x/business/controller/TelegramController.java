package com.x.business.controller;

import com.sharedlib.response.ApiResponse;
import com.x.business.dto.TelegramLinkResponse;
import com.x.business.dto.TelegramNotifyRequest;
import com.x.business.dto.TelegramStatusResponse;
import com.x.business.dto.UpdateTelegramChatRequest;
import com.x.business.dto.UpdateTelegramPreferencesRequest;
import com.x.business.telegram.TelegramService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class TelegramController {

    private final TelegramService telegramService;

    @GetMapping("/api/v1/businesses/{id}/telegram")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> status(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramService.status(id)));
    }

    @PutMapping("/api/v1/businesses/{id}/telegram")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> updatePreferences(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateTelegramPreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), "Telegram alerts updated", telegramService.updatePreferences(id, request)));
    }

    @DeleteMapping("/api/v1/businesses/{id}/telegram")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> disconnect(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), "Telegram disconnected", telegramService.disconnect(id)));
    }

    @PostMapping("/api/v1/businesses/{id}/telegram/link")
    public ResponseEntity<ApiResponse<TelegramLinkResponse>> createLink(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramService.createLink(id)));
    }

    @PostMapping("/api/v1/businesses/{id}/telegram/test")
    public ResponseEntity<ApiResponse<Void>> sendTest(@PathVariable @Positive Long id) {
        telegramService.sendTest(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Test alert sent", null));
    }

    @PutMapping("/api/v1/businesses/{id}/telegram/chats/{chatId}")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> updateChat(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long chatId,
            @Valid @RequestBody UpdateTelegramChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), "Telegram chat updated", telegramService.updateChat(id, chatId, request)));
    }

    @DeleteMapping("/api/v1/businesses/{id}/telegram/chats/{chatId}")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> disconnectChat(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long chatId) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), "Telegram chat disconnected", telegramService.disconnectChat(id, chatId)));
    }

    @PostMapping("/api/v1/businesses/{id}/telegram/chats/{chatId}/test")
    public ResponseEntity<ApiResponse<Void>> sendChatTest(@PathVariable @Positive Long id, @PathVariable @Positive Long chatId) {
        telegramService.sendTest(id, chatId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Test alert sent", null));
    }

    /** Alerts from the payment and order services; sent only if the business wants this kind of alert. */
    @PostMapping("/internal/businesses/{id}/telegram/notify")
    public ResponseEntity<ApiResponse<Boolean>> notify(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TelegramNotifyRequest request) {
        boolean sent = telegramService.notify(id, request.type(), request.storeId(), request.text(), request.parseMode());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), sent ? "Alert sent" : "Alert skipped", sent));
    }
}
