package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record ReingresoCajaRequest(
        BigDecimal monto,
        String concepto,
        String usuario
) {
    public ReingresoCajaRequest {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }
    }
}