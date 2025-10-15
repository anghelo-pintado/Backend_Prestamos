package com.a.prestamos.service;

import com.a.prestamos.model.entity.Cliente;

public interface IClienteService {
    Cliente save(Cliente cliente);
    Cliente verifyById(String dni);
}
