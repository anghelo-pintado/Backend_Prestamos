package com.a.prestamos.controller;

import com.a.prestamos.model.dto.caja.AperturaCajaRequest;
import com.a.prestamos.model.dto.caja.CierreCajaRequest;
import com.a.prestamos.model.dto.caja.ResumenCajaDto;
import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.service.impl.CajaServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CajaController {

    private final CajaServiceImpl cajaService;

    @PostMapping("/caja/abrir")
    public ResponseEntity<Caja> abrirCaja(@RequestBody AperturaCajaRequest request) {
        return ResponseEntity.ok(cajaService.abrirCaja(request));
    }

    @GetMapping("/caja/resumen")
    public ResponseEntity<ResumenCajaDto> obtenerResumen() {
        return ResponseEntity.ok(cajaService.obtenerResumenActual());
    }

    @PostMapping("/caja/cerrar")
    public ResponseEntity<Caja> cerrarCaja(@RequestBody CierreCajaRequest request) {
        return ResponseEntity.ok(cajaService.cerrarCaja(request));
    }
}