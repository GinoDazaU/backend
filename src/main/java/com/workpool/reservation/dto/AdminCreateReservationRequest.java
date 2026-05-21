package com.workpool.reservation.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
public class AdminCreateReservationRequest {

    @NotNull(message = "El espacio es obligatorio")
    private UUID officeId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private OffsetDateTime beginDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private OffsetDateTime endDate;

    @NotNull(message = "La cantidad de asistentes es obligatoria")
    @Min(value = 1, message = "Debe haber al menos 1 asistente")
    private Integer personAmount;

    @NotBlank(message = "El nombre del representante es obligatorio")
    private String representativeName;

    @NotBlank(message = "El apellido del representante es obligatorio")
    private String representativeLastName;

    @NotBlank(message = "El DNI del representante es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe contener 8 dígitos")
    private String representativeDni;

    private UUID userId;
}