package com.x.business.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.x.business.dto.ExchangeRateReferenceResponse;
import com.x.business.entity.ExchangeRateReference;
import com.x.business.repository.ExchangeRateReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Fetches an external reference for display; merchant-approved rates remain separately stored. */
@Service
public class ExchangeRateReferenceService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateReferenceService.class);
    private static final String PAIR = "USD_KHR";
    private static final String PROVIDER = "Frankfurter";
    private static final Duration MANUAL_REFRESH_MINIMUM_INTERVAL = Duration.ofMinutes(5);

    private final ExchangeRateReferenceRepository repository;
    private final RestClient client;
    private final URI latestRateUri;

    public ExchangeRateReferenceService(
            ExchangeRateReferenceRepository repository,
            RestClient.Builder builder,
            @Value("${x.exchange-rate.frankfurter-rate-url}") String latestRateUrl) {
        this.repository = repository;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(6));
        this.client = builder.clone().requestFactory(requestFactory).build();
        this.latestRateUri = URI.create(latestRateUrl);
    }

    /** Refreshes promptly when the service first starts, without making startup depend on the provider. */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshIfEmpty() {
        if (repository.existsById(PAIR)) {
            return;
        }
        refreshQuietly();
    }

    /** Frankfurter publishes business-day rates; the latest available date may be Friday over a weekend. */
    @Scheduled(cron = "${x.exchange-rate.refresh-cron:0 15 6 * * *}", zone = "Asia/Phnom_Penh")
    public void refreshDaily() {
        refreshQuietly();
    }

    public ExchangeRateReferenceResponse latestStored() {
        return repository.findById(PAIR)
                .map(reference -> toResponse(reference,
                        reference.getFetchedAt().plus(Duration.ofHours(36)).isBefore(Instant.now())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Exchange-rate reference is not available yet"));
    }

    public ExchangeRateReferenceResponse refreshLatest() {
        ExchangeRateReference saved = repository.findById(PAIR).orElse(null);
        if (saved != null && saved.getFetchedAt().plus(MANUAL_REFRESH_MINIMUM_INTERVAL).isAfter(Instant.now())) {
            return toResponse(saved, saved.getFetchedAt().plus(Duration.ofHours(36)).isBefore(Instant.now()));
        }
        try {
            return refresh();
        } catch (RestClientException | IllegalArgumentException | DataAccessException exception) {
            log.warn("Could not refresh Frankfurter USD/KHR reference; using the last saved rate if available: {}",
                    exception.getMessage());
            return repository.findById(PAIR)
                    .map(reference -> toResponse(reference, true))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Exchange-rate reference is temporarily unavailable and no saved rate exists"));
        }
    }

    private ExchangeRateReferenceResponse refresh() {
        FrankfurterRateResponse response = client.get()
                .uri(latestRateUri)
                .retrieve()
                .body(FrankfurterRateResponse.class);
        BigDecimal rate = response == null ? null : response.rate();
        if (rate == null || rate.signum() <= 0 || response.date() == null
                || !"USD".equalsIgnoreCase(response.base())
                || !"KHR".equalsIgnoreCase(response.quote())) {
            throw new IllegalArgumentException("Frankfurter returned an invalid USD/KHR reference");
        }
        try {
            ExchangeRateReference reference = repository.save(ExchangeRateReference.builder()
                    .currencyPair(PAIR)
                    .rate(rate)
                    .rateDate(response.date())
                    .fetchedAt(Instant.now())
                    .provider(PROVIDER)
                    .build());
            return toResponse(reference, false);
        } catch (DataAccessException exception) {
            // A second service replica can create the singleton row at the same time during first startup.
            return repository.findById(PAIR)
                    .map(reference -> toResponse(reference, false))
                    .orElseThrow(() -> exception);
        }
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (RestClientException | IllegalArgumentException | DataAccessException exception) {
            log.warn("Frankfurter USD/KHR reference refresh failed; saved reference was retained: {}",
                    exception.getMessage());
        }
    }

    private ExchangeRateReferenceResponse toResponse(ExchangeRateReference reference, boolean stale) {
        return new ExchangeRateReferenceResponse("USD", "KHR", reference.getRate(), reference.getRateDate(),
                reference.getFetchedAt(), reference.getProvider(), stale);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FrankfurterRateResponse(LocalDate date, String base, String quote, BigDecimal rate) {
    }
}
