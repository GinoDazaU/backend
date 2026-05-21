package com.workpool.office;

import com.workpool.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "office_blocks")
public class OfficeBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by", nullable = false)
    private User blockedBy;

    @Column(name = "begin_date", nullable = false)
    private OffsetDateTime beginDate;

    @Column(name = "end_date", nullable = false)
    private OffsetDateTime endDate;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}