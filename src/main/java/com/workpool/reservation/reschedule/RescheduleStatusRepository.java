package com.workpool.reservation.reschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RescheduleStatusRepository extends JpaRepository<RescheduleStatus, UUID> {
    Optional<RescheduleStatus> findByRescheduleStatusName(String name);
}