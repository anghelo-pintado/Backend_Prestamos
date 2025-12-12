package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record ResumenCajaDto(
        Long cajaId,
        String fechaApertura,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo, // Sistema
        BigDecimal ingresosDigital,
        BigDecimal totalReingresos,
        BigDecimal totalVueltos, // Sistema
        BigDecimal totalEsperadoEnCaja, // Saldo Inicial + Ingresos Efectivo
        BigDecimal efectivoDisponible  // Efectivo real disponible para dar vuelto
) {}
