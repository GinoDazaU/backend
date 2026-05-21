package com.workpool.reservation;

import com.workpool.office.Office;
import com.workpool.payment.Payment;
import com.workpool.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reservation_status_id", nullable = false)
    private ReservationStatus reservationStatus;

    @Column(name = "begin_date", nullable = false)
    private OffsetDateTime beginDate;

    @Column(name = "end_date", nullable = false)
    private OffsetDateTime endDate;

    @Column(name = "person_amount", nullable = false)
    private int personAmount;

    @Column(name = "uses_parking", nullable = false)
    private boolean usesParking;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "total_price_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPriceUsd;

    @Column(name = "total_price_pen", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPricePen;

    @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Column(name = "representative_last_name", nullable = false)
    private String representativeLastName;

    @Column(name = "representative_dni", nullable = false)
    private String representativeDni;

    @Column(name = "created_by_admin", nullable = false)
    private boolean createdByAdmin;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reservation_guests",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    private Set<Guest> guests = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reservation_vehicles",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id")
    )
    private Set<Vehicle> vehicles = new HashSet<>();

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "active_slot", nullable = false)
    private boolean activeSlot = true;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return reservationStatus.getStatusName().equals("PENDIENTE");
    }

    public boolean isConfirmed() {
        return reservationStatus.getStatusName().equals("CONFIRMADA");
    }

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }
}