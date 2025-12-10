package com.a.prestamos.service;

import com.a.prestamos.model.dto.mercadoPago.MercadoPagoPreferenceResponse;
import com.a.prestamos.model.dto.pago.PagoRequest;
import com.a.prestamos.model.dto.pago.PagoResponse;

import java.util.List;

public interface IPagoService {
    /**
     * Registra un nuevo pago (efectivo, Yape, Plin, Tarjeta manual).
     *
     * @param request Datos del pago.
     * @return El pago registrado.
     */
    PagoResponse registrarPago(PagoRequest request);

    /**
     * Crea una preferencia de pago en Mercado Pago.
     *
     * @param cuotaId ID de la cuota a pagar.
     * @param monto Monto a pagar (puede ser parcial).
     * @return Información para redirigir al checkout de Mercado Pago.
     */
    MercadoPagoPreferenceResponse crearPreferenciaMercadoPago(Long cuotaId, java.math.BigDecimal monto);

    /**
     * Procesa el webhook de Mercado Pago cuando se confirma un pago.
     *
     * @param paymentId ID del pago en Mercado Pago.
     * @return El pago actualizado.
     */
    PagoResponse procesarWebhookMercadoPago(String paymentId);

    /**
     * Anula un pago existente.
     *
     * @param request Datos de la anulación.
     * @return El pago anulado.
     */
    //PagoResponse anularPago(AnularPagoRequest request);

    /**
     * Obtiene el historial de pagos de una cuota.
     *
     * @param cuotaId ID de la cuota.
     * @return Lista de pagos.
     */
    List<PagoResponse> obtenerPagosPorCuota(Long cuotaId);

    /**
     * Obtiene el historial de pagos de un préstamo.
     *
     * @param prestamoId ID del préstamo.
     * @return Lista de pagos.
     */
    List<PagoResponse> obtenerPagosPorPrestamo(Long prestamoId);

    /**
     * Obtiene el historial de pagos de un cliente por DNI.
     *
     * @param dni DNI del cliente.
     * @return Lista de pagos.
     */
    List<PagoResponse> obtenerPagosPorCliente(String dni);

    /**
     * Obtiene las cuotas de un préstamo con información de mora.
     *
     * @param prestamoId ID del préstamo.
     * @return Lista de cuotas con mora calculada.
     */
    //List<CuotaConMoraDto> obtenerCuotasConMora(Long prestamoId);

    /**
     * Obtiene una cuota específica con información de mora.
     *
     * @param cuotaId ID de la cuota.
     * @return Cuota con mora calculada.
     */
    //CuotaConMoraDto obtenerCuotaConMora(Long cuotaId);

}
