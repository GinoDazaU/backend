package com.workpool.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query(value = """
        SELECT COUNT(*) FROM reservations r
        WHERE r.office_id = :officeId
        AND r.reservation_status_id IN (
            SELECT rs.id FROM reservation_status rs WHERE rs.status_name IN ('PENDIENTE', 'CONFIRMADA')
        )
        AND tstzrange(r.begin_date, r.end_date) && tstzrange(:start, :end)
        """, nativeQuery = true)
    int countOverlapping(UUID officeId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<Reservation> findAllByUserId(UUID userId);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND r.reservationStatus.statusName = 'PENDIENTE' AND r.expiresAt > :now ORDER BY r.createdAt DESC")
    Optional<Reservation> findPendingDraft(UUID userId, OffsetDateTime now);

    @Query("SELECT r FROM Reservation r WHERE r.reservationStatus.statusName = 'PENDIENTE' AND r.expiresAt IS NOT NULL AND r.expiresAt < :now")
    List<Reservation> findExpiredPreReservations(OffsetDateTime now);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.user.id = :userId AND r.reservationStatus.statusName IN ('PENDIENTE', 'CONFIRMADA')")
    long countActiveByUserId(UUID userId);

    @Query(value = """
        SELECT COUNT(*) FROM reservations r
        WHERE r.office_id = :officeId
        AND r.reservation_status_id IN (
            SELECT rs.id FROM reservation_status rs WHERE rs.status_name IN ('PENDIENTE', 'CONFIRMADA')
        )
        """, nativeQuery = true)
    long countActiveByOfficeId(UUID officeId);
}