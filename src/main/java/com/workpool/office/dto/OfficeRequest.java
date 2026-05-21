package com.workpool.office.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.util.UUID;

@Getter
public class OfficeRequest {

    @NotBlank(message = "El nombre del espacio es obligatorio")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "La capacidad es obligatoria")
    @Min(value = 1, message = "La capacidad mínima es 1")
    private Integer capacity;

    @NotNull(message = "El tipo de espacio es obligatorio")
    private UUID officeKindId;

    @NotBlank(message = "Las condiciones de uso son obligatorias")
    private String conditions;
}