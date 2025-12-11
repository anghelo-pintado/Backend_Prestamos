package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Prestamo;
import com.a.prestamos.model.entity.enums.LoanState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrestamoDao extends JpaRepository<Prestamo, Long> {
    boolean existsByCustomerDocumentId(String documentId);


    Optional<Prestamo> findByCustomerDocumentIdAndLoanState(String customerDocumentId, LoanState loanState);

    boolean existsByCustomerDocumentId_AndLoanState(String customerDocumentId, LoanState loanState);
}
