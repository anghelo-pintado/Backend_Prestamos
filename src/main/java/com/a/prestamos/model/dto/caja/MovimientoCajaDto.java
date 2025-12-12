package com.a.prestamos.model.dto.caja;

import com.a.prestamos.model.entity.MovimientoCaja;
import com.a.prestamos.model.entity.enums.TipoMovimientoCaja;
import java.math.BigDecimal;
import java.time.Instant;

public record MovimientoCajaDto(
        Long id,
        TipoMovimientoCaja tipo,
        BigDecimal monto,
        String concepto,
        String usuario,
        Instant fecha,
        BigDecimal saldoResultante
) {
    public static MovimientoCajaDto fromEntity(MovimientoCaja movimiento) {
        return new MovimientoCajaDto(
                movimiento.getId(),
                movimiento.getTipo(),
                movimiento.getMonto(),
                movimiento.getConcepto(),
                movimiento.getUsuario(),
                movimiento.getFecha(),
                movimiento.getSaldoResultante()
        );
    }
}