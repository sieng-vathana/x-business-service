package com.x.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "businesses",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_code", columnNames = "code"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "default_currency_code", nullable = false, length = 3)
    private String defaultCurrencyCode;

    @Column(name = "tax_registration_number", length = 100)
    private String taxRegistrationNumber;

    @Column(name = "tax_registration_label", length = 32)
    private String taxRegistrationLabel;

    /** Tax ID owned by the product/tax service; no cross-service JPA relation. */
    @Column(name = "default_tax_id")
    private Long defaultTaxId;

    @Column(name = "prices_include_tax", nullable = false)
    @Builder.Default
    private Boolean pricesIncludeTax = true;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth;

    @Column(nullable = false)
    private Integer status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
