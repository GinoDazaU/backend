package com.workpool.reservation.reschedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
public class AdminRescheduleRequest {

    @NotNull(message = "El ID de la solicitud es obligatorio")
    private UUID rescheduleRequestId;

    private boolean approved;

    // solo si approved = true
    private UUID newOfficeId;
    private OffsetDateTime newBeginDate;
    private OffsetDateTime newEndDate;
}