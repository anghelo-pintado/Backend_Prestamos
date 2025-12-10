package com.a.prestamos.model.dto.mercadoPago;

import java.math.BigDecimal;

/**
 * DTO con la información para iniciar un pago en Mercado Pago.
 */
public record MercadoPagoPreferenceResponse(
        String preferenceId,
        String initPoint,
        String sandboxInitPoint,
        Long pagoId,
        Long cuotaId,
        BigDecimal monto
) {}
