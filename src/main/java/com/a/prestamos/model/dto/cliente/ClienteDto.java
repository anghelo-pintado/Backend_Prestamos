package com.a.prestamos.model.dto.cliente;

import com.a.prestamos.model.entity.Cliente;

/**
 * DTO que representa la información pública de un cliente.
 * @param dni El DNI del cliente.
 * @param fullName El nombre completo del cliente.
 * @param pep Indica si el cliente es una Persona Expuesta Políticamente.
 */
public record ClienteDto(
        String documentId,
        String firstLastName,
        String secondLastName,
        String fullName,
        boolean pep,
        String firstName) {
    /**
     * Método de fábrica para convertir una entidad Cliente a un ClienteDto.
     * @param cliente La entidad a convertir.
     * @return Un nuevo CustomerDto.
     */
    public static ClienteDto fromEntity(Cliente cliente) {
        return new ClienteDto(
                cliente.getDocumentId(),
                cliente.getFirstLastName(),
                cliente.getSecondLastName(),
                cliente.getFullName(),
                cliente.isPep(),
                cliente.getFirstName()
        );
    }
}

