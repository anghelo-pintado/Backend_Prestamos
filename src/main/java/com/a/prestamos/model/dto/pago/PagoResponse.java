package com.a.prestamos.model.dto.pago;

import com.a.prestamos.model.entity.Pago;
import com.a.prestamos.model.entity.enums.PaymentMethod;
import com.a.prestamos.model.entity.enums.PaymentState;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta con los detalles de un pago registrado.
 */
public record PagoResponse(
        Long id,
        Long cuotaId,
        Integer numeroCuota,
        BigDecimal montoPagado,
        BigDecimal montoRecibido,
        BigDecimal vuelto,
        BigDecimal ajusteRedondeo,
        BigDecimal montMora,
        Boolean moraPerdonada,
        PaymentMethod metodoPago,
        Instant fechaPago,
        String numeroOperacion,
        PaymentState estado,
        String comprobanteSerie,
        Long comprobanteNumero,
        String comprobanteTipo,
        String comprobanteUrl,
        String observaciones,
        // Información adicional del contexto
        String clienteNombre,
        String clienteDni,
        BigDecimal saldoPendienteCuota,
        String estadoCuota
) {
    /**
     * Convierte una entidad Pago a PagoResponse.
     */
    public static PagoResponse fromEntity(Pago pago) {
        // Verificar si existe comprobante (puede ser null para pagos de MP)
        String serie = null;
        Long sequential = null;
        String proofType = null;
        String urlPdf = null;

        if (pago.getProofPayment() != null) {
            serie = pago.getProofPayment().getSerie();
            sequential = pago.getProofPayment().getSequential();
            proofType = pago.getProofPayment().getProofType();
            urlPdf = pago.getProofPayment().getUrlPdf();
        }

        return new PagoResponse(
                pago.getId(),
                pago.getInstallment().getId(),
                pago.getInstallment().getNum(),
                pago.getAmountPaid(),
                pago.getAmountReceived(),
                pago.getChange(),
                pago.getRounding(),
                pago.getMontMora(),
                pago.getMoraPerdonada(),
                pago.getPaymentMethod(),
                pago.getPaymentDate(),
                pago.getOperationTrace(),
                pago.getPaymentState(),
                serie,
                sequential,
                proofType,
                urlPdf,
                pago.getObservations(),
                pago.getInstallment().getLoan().getCustomer().getFullName(),
                pago.getInstallment().getLoan().getCustomer().getDocumentId(),
                pago.getInstallment().getBalance(),
                pago.getInstallment().getInstallmentState().name()
        );
    }
}