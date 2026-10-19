package com.x.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Latest external market reference; it never changes a merchant's approved POS rate. */
@Entity
@Table(name = "exchange_rate_references")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateReference {

    @Id
    @Column(name = "currency_pair", nullable = false, length = 16)
    private String currencyPair;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false, length = 64)
    private String provider;
}
