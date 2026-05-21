package com.workpool.reservation.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
public class CreateReservationRequest {

    @NotNull(message = "El espacio es obligatorio")
    private UUID officeId;

    @NotNull(message = "El plan de precios es obligatorio")
    private UUID officePlanId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private OffsetDateTime beginDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private OffsetDateTime endDate;

    @NotNull(message = "La cantidad de asistentes es obligatoria")
    @Min(value = 1, message = "Debe haber al menos 1 asistente")
    private Integer personAmount;

    private boolean usesParking;

    @NotBlank(message = "El nombre del representante es obligatorio")
    private String representativeName;

    @NotBlank(message = "El apellido del representante es obligatorio")
    private String representativeLastName;

    @NotBlank(message = "El DNI del representante es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe contener exactamente 8 dígitos")
    private String representativeDni;

    private List<GuestItem> guests;

    private List<String> vehiclePlates;

    @Getter
    public static class GuestItem {
        @NotBlank(message = "El nombre del invitado es obligatorio")
        private String name;

        @NotBlank(message = "El apellido del invitado es obligatorio")
        private String lastName;

        @NotBlank(message = "El DNI del invitado es obligatorio")
        @Pattern(regexp = "^\\d{8}$", message = "El DNI del invitado debe contener 8 dígitos")
        private String dni;
    }
}