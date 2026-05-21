package com.workpool.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ConfirmPaymentRequest {

    @NotNull(message = "El ID de la reserva es obligatorio")
    private UUID reservationId;
}