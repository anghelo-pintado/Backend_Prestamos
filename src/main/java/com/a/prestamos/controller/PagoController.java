package com.a.prestamos.controller;

import com.a.prestamos.model.dto.mercadoPago.MercadoPagoPreferenceResponse;
import com.a.prestamos.model.dto.pago.*;
import com.a.prestamos.service.IPagoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PagoController {

    private final IPagoService pagoService;

    /**
     * Registra un nuevo pago (efectivo, Yape, Plin, Tarjeta).
     *
     * POST /api/v1/pagos
     */
    @PostMapping("/pagos")
    public ResponseEntity<PagoResponse> registrarPago(@Valid @RequestBody PagoRequest request) {
        log.info("Registrando pago para cuota ID: {}, método: {}", request.cuotaId(), request.metodoPago());
        PagoResponse response = pagoService.registrarPago(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Crea una preferencia de pago en Mercado Pago.
     *
     * POST /api/v1/pagos/mercadopago/preferencia
     */
    @PostMapping("/pagos/mercadopago/preferencia")
    public ResponseEntity<MercadoPagoPreferenceResponse> crearPreferenciaMercadoPago(
            @RequestParam Long cuotaId,
            @RequestParam BigDecimal monto) {
        log.info("Creando preferencia MP para cuota ID: {}, monto: {}", cuotaId, monto);
        MercadoPagoPreferenceResponse response = pagoService.crearPreferenciaMercadoPago(cuotaId, monto);
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook para recibir notificaciones de Mercado Pago.
     *
     * POST /api/v1/pagos/mercadopago/webhook
     */
    @PostMapping("/pagos/mercadopago/webhook")
    public ResponseEntity<String> webhookMercadoPago(@RequestBody Map<String, Object> payload) {
        log.info("Webhook MP recibido: {}", payload);

        try {
            String type = (String) payload.get("type");

            if ("payment".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                String paymentId = String.valueOf(data.get("id"));

                pagoService.procesarWebhookMercadoPago(paymentId);
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error procesando webhook MP: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK"); // Siempre responder OK para evitar reintentos
        }
    }

    /**
     * Anula un pago existente.
     *
     * POST /api/v1/pagos/anular
     */
//    @PostMapping("/anular")
//    public ResponseEntity<PagoResponse> anularPago(@Valid @RequestBody AnularPagoRequest request) {
//        log.info("Anulando pago ID: {}", request.pagoId());
//        PagoResponse response = pagoService.anularPago(request);
//        return ResponseEntity.ok(response);
//    }

    /**
     * Obtiene el historial de pagos de una cuota.
     *
     * GET /api/v1/pagos/cuota/{cuotaId}
     */
    @GetMapping("/cuota/{cuotaId}")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorCuota(@PathVariable Long cuotaId) {
        List<PagoResponse> pagos = pagoService.obtenerPagosPorCuota(cuotaId);
        return ResponseEntity.ok(pagos);
    }

    /**
     * Obtiene el historial de pagos de un préstamo.
     *
     * GET /api/v1/pagos/prestamo/{prestamoId}
     */
    @GetMapping("/prestamo/{prestamoId}")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorPrestamo(@PathVariable Long prestamoId) {
        List<PagoResponse> pagos = pagoService.obtenerPagosPorPrestamo(prestamoId);
        return ResponseEntity.ok(pagos);
    }

    /**
     * Obtiene el historial de pagos de un cliente por DNI.
     *
     * GET /api/v1/pagos/cliente?dni=12345678
     */
    @GetMapping("/cliente")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorCliente(
            @RequestParam @Pattern(regexp = "\\d{8}", message = "El DNI debe contener 8 dígitos.") String dni) {
        List<PagoResponse> pagos = pagoService.obtenerPagosPorCliente(dni);
        return ResponseEntity.ok(pagos);
    }

    /**
     * Obtiene las cuotas de un préstamo con información de mora.
     *
     * GET /api/v1/pagos/cuotas-mora/prestamo/{prestamoId}
     */
//    @GetMapping("/cuotas-mora/prestamo/{prestamoId}")
//    public ResponseEntity<List<CuotaConMoraDto>> obtenerCuotasConMora(@PathVariable Long prestamoId) {
//        List<CuotaConMoraDto> cuotas = pagoService.obtenerCuotasConMora(prestamoId);
//        return ResponseEntity.ok(cuotas);
//    }

    /**
     * Obtiene una cuota específica con información de mora.
     *
     * GET /api/v1/pagos/cuota-mora/{cuotaId}
     */
//    @GetMapping("/cuota-mora/{cuotaId}")
//    public ResponseEntity<CuotaConMoraDto> obtenerCuotaConMora(@PathVariable Long cuotaId) {
//        CuotaConMoraDto cuota = pagoService.obtenerCuotaConMora(cuotaId);
//        return ResponseEntity.ok(cuota);
//    }
}