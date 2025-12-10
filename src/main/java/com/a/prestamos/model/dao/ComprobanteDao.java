package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComprobanteDao extends JpaRepository<Comprobante, Long> {
    long countComprobantesBySerie(String serie);

    boolean existsByPaymentId(Long paymentId);

    List<Comprobante> findByPaymentInstallmentId(Long cuotaId);
}
