package com.workpool.reservation.reschedule.dto;

import com.workpool.reservation.reschedule.RescheduleRequest;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
public class RescheduleResponse {

    private final UUID id;
    private final UUID reservationId;
    private final String userEmail;
    private final String status;
    private final OffsetDateTime createdAt;

    private RescheduleResponse(UUID id, UUID reservationId, String userEmail,
                               String status, OffsetDateTime createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.userEmail = userEmail;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static RescheduleResponse from(RescheduleRequest r) {
        return new RescheduleResponse(
                r.getId(),
                r.getReservation().getId(),
                r.getUser().getEmail(),
                r.getRescheduleStatus().getRescheduleStatusName(),
                r.getCreatedAt()
        );
    }
}