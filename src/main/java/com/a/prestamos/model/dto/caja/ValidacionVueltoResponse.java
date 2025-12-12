package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record ValidacionVueltoResponse(
        boolean suficiente,
        BigDecimal efectivoDisponible,
        BigDecimal vueltoRequerido,
        BigDecimal faltante,
        String mensaje
) {
    public static ValidacionVueltoResponse crear(
            BigDecimal efectivoDisponible,
            BigDecimal vueltoRequerido
    ) {
        BigDecimal faltante = BigDecimal.ZERO;
        boolean suficiente = efectivoDisponible.compareTo(vueltoRequerido) >= 0;

        if (!suficiente) {
            faltante = vueltoRequerido.subtract(efectivoDisponible);
        }

        String mensaje = suficiente
                ? "✅ Hay suficiente efectivo en caja para dar el vuelto"
                : String.format("⚠️ Faltan S/ %.2f en caja. Efectivo disponible: S/ %.2f, Vuelto requerido: S/ %.2f",
                faltante, efectivoDisponible, vueltoRequerido);

        return new ValidacionVueltoResponse(
                suficiente,
                efectivoDisponible,
                vueltoRequerido,
                faltante,
                mensaje
        );
    }
}