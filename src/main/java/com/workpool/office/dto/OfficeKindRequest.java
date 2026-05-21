package com.workpool.office.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class OfficeKindRequest {

    @NotBlank(message = "El nombre del tipo de espacio es obligatorio")
    private String kindName;
}