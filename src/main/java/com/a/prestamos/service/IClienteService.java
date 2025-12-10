package com.a.prestamos.service;

import com.a.prestamos.model.entity.Cliente;

public interface IClienteService {
    void save(Cliente cliente);
    Cliente verifyByDocumentId(String dni);
}
