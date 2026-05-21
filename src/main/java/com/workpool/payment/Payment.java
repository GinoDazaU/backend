package com.workpool.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "amount_pen", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPen;

    @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}