package com.x.business.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puts live events on the x.realtime topic; x-notification-service pushes them to the browsers they are for, so
 * screens update at once instead of asking again and again. Inside a transaction the event waits for the commit,
 * so a screen that reloads sees the change.
 */
@Component
public class RealtimePublisher {

    static final String TOPIC = "x.realtime";
    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final ObjectProvider<KafkaTemplate<String, String>> kafka;
    private final ObjectMapper objectMapper;
    // A slow or missing broker must never hold up the request or job that caused the event.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "realtime-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimePublisher(ObjectProvider<KafkaTemplate<String, String>> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    /** To everyone signed in to the business. */
    public void toBusiness(Long businessId, String type, Map<String, ?> data) {
        if (businessId != null) send(type, Map.of("businessId", businessId), data);
    }

    private void send(String type, Map<String, Object> audience, Map<String, ?> data) {
        KafkaTemplate<String, String> template = kafka.getIfAvailable();
        if (template == null) return;
        Map<String, Object> event = new HashMap<>(audience);
        event.put("type", type);
        event.put("data", data == null ? Map.of() : data);
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.warn("Realtime {} could not be written: {}", type, exception.getOriginalMessage());
            return;
        }
        Runnable publish = () -> sender.execute(() -> {
            try {
                template.send(TOPIC, json);
            } catch (RuntimeException exception) {
                log.warn("Realtime {} not sent: {}", type, exception.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
