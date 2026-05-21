package com.workpool.reservation.reschedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateRescheduleRequest {

    @NotNull(message = "El ID de la reserva es obligatorio")
    private UUID reservationId;
}