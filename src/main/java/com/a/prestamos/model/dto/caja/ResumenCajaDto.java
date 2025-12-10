package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record ResumenCajaDto(
        Long cajaId,
        String fechaApertura,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo, // Sistema
        BigDecimal ingresosDigital,  // Sistema
        BigDecimal totalEsperadoEnCaja // Saldo Inicial + Ingresos Efectivo
) {}
