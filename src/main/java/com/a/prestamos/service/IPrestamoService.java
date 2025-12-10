package com.a.prestamos.service;

import com.a.prestamos.model.dto.prestamo.PrestamoRequest;
import com.a.prestamos.model.entity.Prestamo;

public interface IPrestamoService {
    /**
     * Orquesta la creación de un nuevo préstamo y su cronograma de pagos.
     *
     * @param request DTO con la información del préstamo a crear.
     * @return La entidad Loan creada y persistida.
     */
    Prestamo createLoan(PrestamoRequest request);

    /**
     * Busca un préstamo existente a partir del DNI del cliente.
     *
     * @param documentId El DNI del cliente.
     * @return La entidad Loan encontrada.
     */
    Prestamo findLoanByDocumentId(String documentId);
}
