package com.workpool.office;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "offices")
public class Office {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "office_kind_id", nullable = false)
    private OfficeKind officeKind;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

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