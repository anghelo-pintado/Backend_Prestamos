package com.a.prestamos.model.dto.prestamo;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrestamoRequest(
        @NotBlank(message = "El DNI no puede estar vacío.")
        @Pattern(regexp = "\\d{8}", message = "El DNI debe contener 8 dígitos.")
        String dni,

        @NotNull(message = "El monto principal es requerido.")
        @Positive(message = "El monto principal debe ser mayor a cero.")
        BigDecimal principal,

        @NotNull(message = "La TEA es requerida.")
        @DecimalMin(value = "0.0", message = "La TEA no puede ser negativa.")
        BigDecimal teaAnnual,

        @NotNull(message = "El plazo en meses es requerido.")
        @Min(value = 1, message = "El plazo debe ser de al menos 1 mes.")
        Integer months,

        @NotNull(message = "El campo PEP es requerido.")
        boolean pep,

        LocalDate startDate
) {
}
