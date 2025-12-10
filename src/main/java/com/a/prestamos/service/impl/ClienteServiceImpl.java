package com.a.prestamos.service.impl;

import com.a.prestamos.client.DocumentConsumer;
import com.a.prestamos.model.dao.ClienteDao;
import com.a.prestamos.model.dto.apiclient.ReniecResponseDto;
import com.a.prestamos.model.dto.apiclient.SunatResponseDto;
import com.a.prestamos.model.entity.Cliente;
import com.a.prestamos.model.entity.enums.DocumentType;
import com.a.prestamos.service.IClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ClienteServiceImpl implements IClienteService {
    @Autowired
    private ClienteDao clienteDao;

    @Autowired
    private DocumentConsumer documentConsumer;

    @Override
    @Transactional
    public void save(Cliente cliente) {
        clienteDao.save(cliente);
    }

    @Override
    public Cliente verifyByDocumentId(String documentId) {
        if (documentId == null || !documentId.matches("\\d{8}|\\d{11}")) {
            throw new IllegalArgumentException("Debe ser DNI (8 dígitos) o RUC (11 dígitos).");
        }

        // Verificar si ya existe
        Optional<Cliente> existing = clienteDao.findById(documentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Crear nuevo cliente según tipo de documento
        Cliente nuevoCliente = new Cliente();

        if (documentId.length() == 8) {
            // Es DNI - consultar RENIEC
            nuevoCliente = crearClienteDesdeReniec(documentId);
        } else {
            // Es RUC - consultar SUNAT
            nuevoCliente = crearClienteDesdeSunat(documentId);
        }

        return clienteDao.save(nuevoCliente);
    }

    private Cliente crearClienteDesdeReniec(String dni) {
        ReniecResponseDto data = documentConsumer.verifyByDni(dni);

        if (data == null || data.documentNumber() == null) {
            throw new RuntimeException("DNI no encontrado en RENIEC.");
        }

        Cliente cliente = new Cliente();
        cliente.setDocumentId(data.documentNumber());
        cliente.setDocumentType(DocumentType.DNI);
        cliente.setFullName(data.fullName());
        cliente.setFirstName(data.firstName());
        cliente.setFirstLastName(data.firstLastName());
        cliente.setSecondLastName(data.secondLastName());
        cliente.setPep(false);

        return cliente;
    }

    private Cliente crearClienteDesdeSunat(String ruc) {
        SunatResponseDto data = documentConsumer.verifyByRuc(ruc);

        if (data == null || data.numeroDocumento() == null) {
            throw new RuntimeException("RUC no encontrado en SUNAT.");
        }

        if (!"ACTIVO".equalsIgnoreCase(data.estado())) {
            throw new RuntimeException("El RUC no está activo. Estado: " + data.estado());
        }

        Cliente cliente = new Cliente();
        cliente.setDocumentId(data.numeroDocumento());
        cliente.setDocumentType(DocumentType.RUC);
        cliente.setFullName(data.razonSocial());
        cliente.setFirstName(data.razonSocial()); // Para empresas, usar razón social
        cliente.setAddress(data.direccion());
        cliente.setPep(false);

        return cliente;
    }
}
