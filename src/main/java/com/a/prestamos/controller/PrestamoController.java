package com.a.prestamos.controller;

import com.a.prestamos.model.dto.prestamo.DetallePrestamoDto;
import com.a.prestamos.model.dto.prestamo.PrestamoRequest;
import com.a.prestamos.model.entity.Prestamo;
import com.a.prestamos.service.IPrestamoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@Validated
public class PrestamoController {
    @Autowired
    private IPrestamoService loanService;

    /**
     * Endpoint para crear un nuevo préstamo.
     *
     * @param request DTO con los datos del préstamo a crear.
     * @return Respuesta HTTP 201 Created con el detalle del préstamo.
     */
    @PostMapping("/prestamo")
    public ResponseEntity<?> createLoan(@Valid @RequestBody PrestamoRequest request) {
        Prestamo newLoan = loanService.createLoan(request);

        // Construir la URI del nuevo recurso creado para el header 'Location'
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(newLoan.getId())
                .toUri();

        DetallePrestamoDto responseDto = DetallePrestamoDto.fromEntity(newLoan);
        return ResponseEntity.created(location).body(responseDto);
    }

    /**
     * Endpoint para buscar un préstamo por el DNI del cliente.
     *
     * @param documentId El DNI de 8 dígitos del cliente.
     * @return Respuesta HTTP 200 OK con el detalle del préstamo encontrado.
     */
    @GetMapping("/prestamo")
    public ResponseEntity<?> findLoanByDocumentId(
            @RequestParam
            @Pattern(regexp = "\\d{8}|\\d{11}", message = "DNI/RUC.")
            String documentId) {

        Prestamo foundLoan = loanService.findLoanByDocumentId(documentId);
        DetallePrestamoDto responseDto = DetallePrestamoDto.fromEntity(foundLoan);

        return ResponseEntity.ok(responseDto);
    }
}
