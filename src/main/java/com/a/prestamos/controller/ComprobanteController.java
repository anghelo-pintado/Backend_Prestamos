package com.a.prestamos.controller;

import com.a.prestamos.model.dao.ComprobanteDao;
import com.a.prestamos.model.dto.comprobante.ComprobanteDto;
import com.a.prestamos.model.entity.Comprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteDao comprobanteDao;

    @GetMapping("/comprobantes/cuota/{cuotaId}")
    public ResponseEntity<List<ComprobanteDto>> listarPorCuota(@PathVariable Long cuotaId) {

        List<Comprobante> comprobantes = comprobanteDao.findByPaymentInstallmentId(cuotaId);

        List<ComprobanteDto> dtos = comprobantes.stream()
                .map(ComprobanteDto::fromEntity)
                .toList();

        return ResponseEntity.ok(dtos);
    }
}
