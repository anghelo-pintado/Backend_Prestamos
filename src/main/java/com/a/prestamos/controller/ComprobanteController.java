package com.a.prestamos.controller;

import com.a.prestamos.model.dto.comprobante.ComprobanteDto;
import com.a.prestamos.model.entity.Comprobante;
import com.a.prestamos.service.IComprobanteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ComprobanteController {

    private final IComprobanteService comprobanteService;

    @GetMapping("/comprobantes/cuota/{cuotaId}")
    public ResponseEntity<List<ComprobanteDto>> listarPorCuota(@PathVariable Long cuotaId) {
        return ResponseEntity.ok(comprobanteService.buscarPorCuota(cuotaId));
    }
}
