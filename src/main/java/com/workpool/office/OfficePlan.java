package com.workpool.office;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "office_plans")
public class OfficePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "office_kind_id", nullable = false)
    private OfficeKind officeKind;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "plan_duration_hours", nullable = false)
    private int planDurationHours;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}