package com.a.prestamos.model.dto.caja;

import com.a.prestamos.model.entity.MovimientoCaja;
import com.a.prestamos.model.entity.enums.TipoMovimientoCaja;
import java.math.BigDecimal;
import java.time.Instant;

public record ReingresoCajaResponse(
        Long id,
        Long cajaId,
        TipoMovimientoCaja tipo,
        BigDecimal monto,
        String concepto,
        String usuario,
        Instant fecha,
        BigDecimal saldoResultante,
        BigDecimal efectivoDisponible
) {
    public static ReingresoCajaResponse fromEntity(MovimientoCaja movimiento, BigDecimal efectivoDisponible) {
        return new ReingresoCajaResponse(
                movimiento.getId(),
                movimiento.getCaja().getId(),
                movimiento.getTipo(),
                movimiento.getMonto(),
                movimiento.getConcepto(),
                movimiento.getUsuario(),
                movimiento.getFecha(),
                movimiento.getSaldoResultante(),
                efectivoDisponible
        );
    }
}