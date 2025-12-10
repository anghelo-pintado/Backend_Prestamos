package com.a.prestamos.controller;

import com.a.prestamos.model.dto.cliente.ClienteDto;
import com.a.prestamos.model.dto.cliente.VerificarClienteRequest;
import com.a.prestamos.model.entity.Cliente;
import com.a.prestamos.service.IClienteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ClienteController {
    @Autowired
    private IClienteService clienteService;

    /**
     * Endpoint para verificar un DNI contra un servicio externo y crear el cliente si no existe.
     *
     * @param request El cuerpo de la solicitud conteniendo el DNI.
     * @return Una respuesta HTTP 200 OK con los datos del cliente si la verificación es exitosa.
     */
    @PostMapping("/cliente/verificar")
    public ResponseEntity<?> verifyCustomer(@Valid @RequestBody VerificarClienteRequest request) {
        try {
            Cliente verifiedCustomer = clienteService.verifyByDocumentId(request.documentId());
            ClienteDto responseDto = ClienteDto.fromEntity(verifiedCustomer);
            return ResponseEntity.ok(responseDto);
        } catch (Exception ex) {
            return ResponseEntity.status(400).body("Error al verificar al cliente: " + ex.getMessage());
        }
    }

}
