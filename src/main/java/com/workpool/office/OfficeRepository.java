package com.workpool.office;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.workpool.reservation.Reservation;

import java.util.List;
import java.util.UUID;

public interface OfficeRepository extends JpaRepository<Office, UUID> {

    List<Office> findAllByEnabledTrue();

    @Query("SELECT o FROM Office o WHERE o.enabled = true AND o.officeKind.id = :kindId AND o.capacity >= :minCapacity")
    List<Office> findAvailable(UUID kindId, int minCapacity);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.office.id = :officeId AND r.reservationStatus.statusName IN ('PENDIENTE', 'CONFIRMADA')")
    long countActiveReservations(UUID officeId);
}