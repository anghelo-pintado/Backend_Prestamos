package com.a.prestamos.model.dto.pago;

import com.a.prestamos.model.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para registrar un nuevo pago.
 *
 * @param cuotaId ID de la cuota a pagar.
 * @param montoPagado Monto que se aplica a la deuda.
 * @param montoRecibido Monto que el cliente entrega (para efectivo, puede ser mayor para dar vuelto).
 * @param metodoPago Método de pago utilizado.
 * @param numeroOperacion Referencia para pagos digitales (opcional).
 * @param observaciones Notas adicionales (opcional).
 * @param emitirComprobante Si se debe emitir comprobante electrónico.
 * @param tipoComprobante BOLETA o FACTURA.
 * @param rucFactura RUC del cliente (requerido si es FACTURA).
 * @param razonSocialFactura Razón social (requerido si es FACTURA).
 */
public record PagoRequest(
        @NotNull(message = "El ID de la cuota es requerido")
        Long cuotaId,

        @NotNull(message = "El monto pagado es requerido")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        BigDecimal montoPagado,

        BigDecimal montoRecibido,

        @NotNull(message = "El método de pago es requerido")
        PaymentMethod metodoPago,

        String numeroOperacion,

        String observaciones,

        Boolean emitirComprobante,

        String tipoComprobante,

        String rucFactura,

        String razonSocialFactura
) {
    public PagoRequest {
        // Valores por defecto
        if (emitirComprobante == null) emitirComprobante = true;
        if (tipoComprobante == null) tipoComprobante = "BOLETA";
    }
}
