package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record AperturaCajaRequest(
        BigDecimal saldoInicial,
        String usuario // Opcional si lo sacas del token
) {}
