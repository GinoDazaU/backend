package com.workpool.reservation.reschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, UUID> {
    boolean existsByReservationId(UUID reservationId);
}