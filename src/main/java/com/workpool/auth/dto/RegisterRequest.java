package com.workpool.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    private String lastName;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo inválido")
    private String email;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^9\\d{8}$", message = "El teléfono debe ser un número peruano de 9 dígitos")
    private String phoneNumber;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe contener exactamente 8 dígitos")
    private String dni;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private java.time.LocalDate birthDate;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "La contraseña debe contener mayúsculas, minúsculas y números"
    )
    private String password;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    private String confirmPassword;

    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }

    @AssertTrue(message = "Debes aceptar los términos y condiciones")
    private boolean termsAccepted;
}