package com.a.prestamos.controller;

import com.a.prestamos.model.dto.caja.*;
import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.service.impl.CajaServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/caja")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CajaController {

    private final CajaServiceImpl cajaService;

    /**
     * Abrir una nueva caja
     * POST /api/v1/caja/abrir
     */
    @PostMapping("/abrir")
    public ResponseEntity<Caja> abrirCaja(@RequestBody AperturaCajaRequest request) {
        return ResponseEntity.ok(cajaService.abrirCaja(request));
    }

    /**
     * Obtener resumen de la caja actual (incluye efectivo disponible)
     * GET /api/v1/caja/resumen
     */
    @GetMapping("/resumen")
    public ResponseEntity<ResumenCajaDto> obtenerResumen() {
        return ResponseEntity.ok(cajaService.obtenerResumenActual());
    }

    /**
     * Cerrar la caja actual
     * POST /api/v1/caja/cerrar
     */
    @PostMapping("/cerrar")
    public ResponseEntity<Caja> cerrarCaja(@RequestBody CierreCajaRequest request) {
        return ResponseEntity.ok(cajaService.cerrarCaja(request));
    }

    // ========== NUEVOS ENDPOINTS ==========

    /**
     * Registrar un reingreso de dinero a la caja
     * POST /api/v1/caja/reingreso
     * Body: { "monto": 100.00, "concepto": "...", "usuario": "..." }
     */
    @PostMapping("/reingreso")
    public ResponseEntity<ReingresoCajaResponse> registrarReingreso(
            @RequestBody ReingresoCajaRequest request
    ) {
        return ResponseEntity.ok(cajaService.registrarReingreso(request));
    }

    /**
     * Validar si hay suficiente efectivo para dar vuelto
     * GET /api/v1/caja/validar-vuelto?monto=80.00
     */
    @PostMapping("/validar-vuelto")
    public ResponseEntity<ValidacionVueltoResponse> validarVuelto(
            @RequestBody ValidarVueltoRequest request
    ) {
        BigDecimal vueltoNecesario = request.montoRecibido()
                .subtract(request.montoPagar());

        return ResponseEntity.ok(
                cajaService.validarEfectivoParaVuelto(vueltoNecesario)
        );
    }

    /**
     * Obtener el efectivo disponible actual
     * GET /api/v1/caja/efectivo-disponible
     */
    @GetMapping("/efectivo-disponible")
    public ResponseEntity<BigDecimal> obtenerEfectivoDisponible() {
        ResumenCajaDto resumen = cajaService.obtenerResumenActual();
        return ResponseEntity.ok(resumen.efectivoDisponible());
    }

    /**
     * Obtener historial de movimientos de la caja actual
     * GET /api/v1/caja/movimientos
     */
    @GetMapping("/movimientos")
    public ResponseEntity<List<MovimientoCajaDto>> obtenerMovimientos() {
        return ResponseEntity.ok(cajaService.obtenerMovimientos());
    }
}