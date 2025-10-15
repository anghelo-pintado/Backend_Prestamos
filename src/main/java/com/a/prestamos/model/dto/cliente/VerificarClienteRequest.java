package com.a.prestamos.model.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para la solicitud de verificación de cliente.
 * @param dni El DNI de 8 dígitos a verificar.
 */
public record VerificarClienteRequest(
        @NotBlank(message = "El DNI no puede estar vacío.")
        @Pattern(regexp = "\\d{8}", message = "El DNI debe contener exactamente 8 dígitos.")
        String dni
) {
}
