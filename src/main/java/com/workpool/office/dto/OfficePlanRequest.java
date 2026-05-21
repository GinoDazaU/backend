package com.workpool.office.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class OfficePlanRequest {

    @NotNull(message = "El tipo de espacio es obligatorio")
    private UUID officeKindId;

    @NotNull(message = "El precio por hora es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal pricePerHour;

    @NotNull(message = "La duración del plan es obligatoria")
    @Min(value = 1, message = "La duración mínima es 1 hora")
    private Integer planDurationHours;
}