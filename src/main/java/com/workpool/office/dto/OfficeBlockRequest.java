package com.workpool.office.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
public class OfficeBlockRequest {

    @NotNull(message = "El espacio es obligatorio")
    private UUID officeId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private OffsetDateTime beginDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private OffsetDateTime endDate;

    @NotBlank(message = "El motivo del bloqueo es obligatorio")
    private String reason;
}