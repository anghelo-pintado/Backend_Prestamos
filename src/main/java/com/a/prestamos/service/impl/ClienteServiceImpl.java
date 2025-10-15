package com.a.prestamos.service.impl;

import com.a.prestamos.client.ReniecConsumer;
import com.a.prestamos.model.dao.ClienteDao;
import com.a.prestamos.model.dto.ReniecResponseDto;
import com.a.prestamos.model.entity.Cliente;
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
    private ReniecConsumer reniecConsumer;

    @Override
    @Transactional
    public Cliente save(Cliente cliente) {
        return clienteDao.save(cliente);
    }

    @Override
    public Cliente verifyById(String dni) {
        // 1. Validar formato de DNI
        if (dni == null || !dni.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos numéricos.");
        }

        // 2. Verificar si el cliente ya existe en nuestra BD para evitar llamadas innecesarias
        Optional<Cliente> existingCustomer = clienteDao.findById(dni);
        if (existingCustomer.isPresent()) {
            return existingCustomer.get();
        }

        // 3. Llamar a la API de RENIEC para verificar el DNI
        ReniecResponseDto reniecData = reniecConsumer.verifyByDni(dni);

        if (reniecData == null || reniecData.documentNumber() == null) {
            throw new RuntimeException("No se encontró información para el DNI proporcionado en RENIEC.");
        }

        Cliente nuevoCliente = new Cliente();
        nuevoCliente.setDni(reniecData.documentNumber());
        nuevoCliente.setFullName(reniecData.fullName());
        nuevoCliente.setFirstName(reniecData.firstName());
        nuevoCliente.setFirstLastName(reniecData.firstLastName());
        nuevoCliente.setSecondLastName(reniecData.secondLastName());

        return clienteDao.save(nuevoCliente);
    }
}
