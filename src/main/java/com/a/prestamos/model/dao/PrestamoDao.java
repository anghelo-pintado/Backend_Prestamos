package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrestamoDao extends JpaRepository<Prestamo, Long> {
    boolean existsByCustomerDni(String dni);
    Optional<Prestamo> findByCustomerDni(String dni);
}
