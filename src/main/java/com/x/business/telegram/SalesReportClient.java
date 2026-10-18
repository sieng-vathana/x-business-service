package com.x.business.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Reads store names and payment totals from the store and payment services for the daily summary. */
@Component
public class SalesReportClient {

    private final RestClient storeClient;
    private final RestClient paymentClient;

    public SalesReportClient(
            RestClient.Builder builder,
            @Value("${services.store.base-url:http://127.0.0.1:8087}") String storeBaseUrl,
            @Value("${services.payment.base-url:http://127.0.0.1:8084}") String paymentBaseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.storeClient = builder.clone().requestFactory(requestFactory).baseUrl(storeBaseUrl + "/api/v1/stores").build();
        this.paymentClient = builder.clone().requestFactory(requestFactory).baseUrl(paymentBaseUrl + "/api/v1/payments").build();
    }

    public List<StoreName> stores(Long businessId) {
        JsonNode response = storeClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("businessId", businessId)
                        .queryParam("page", 0)
                        .queryParam("size", 100)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        List<StoreName> stores = new ArrayList<>();
        if (response != null) {
            for (JsonNode store : response.path("data").path("content")) {
                stores.add(new StoreName(store.path("id").asLong(), store.path("name").asText("Store")));
            }
        }
        return stores;
    }

    /** Paid and refunded payments created in [from, to), grouped by method and provider. */
    public List<PaymentTotal> paymentTotals(Long storeId, String currencyCode, LocalDateTime from, LocalDateTime to) {
        JsonNode response = paymentClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/reports/breakdown")
                        .queryParam("storeId", storeId)
                        .queryParam("currencyCode", currencyCode)
                        .queryParam("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .queryParam("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build())
                .retrieve()
                .body(JsonNode.class);
        List<PaymentTotal> totals = new ArrayList<>();
        if (response != null) {
            for (JsonNode row : response.path("data")) {
                totals.add(new PaymentTotal(
                        row.path("method").asText(),
                        row.path("provider").asText(),
                        row.path("paymentCount").asLong(),
                        row.path("totalAmount").decimalValue(),
                        row.path("refundedAmount").decimalValue()));
            }
        }
        return totals;
    }

    public record StoreName(Long id, String name) {
    }

    public record PaymentTotal(
            String method, String provider, long paymentCount, BigDecimal totalAmount, BigDecimal refundedAmount) {
    }
}
