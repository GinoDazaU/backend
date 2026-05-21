package com.workpool.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReservationStatusRepository extends JpaRepository<ReservationStatus, UUID> {
    Optional<ReservationStatus> findByStatusName(String statusName);
}