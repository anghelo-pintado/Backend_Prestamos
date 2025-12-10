package com.a.prestamos.model.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para la solicitud de verificación de cliente.
 * @param documentId El DNI/RUC a verificar.
 */
public record VerificarClienteRequest(
        @NotBlank(message = "El DNI/RUC no puede estar vacío.")
        @Pattern(regexp = "\\d{8}|\\d{11}", message = "Debe ser DNI (8 dígitos) o RUC (11 dígitos).")
        String documentId
) {
}
