package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.model.entity.enums.CajaState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CajaDao extends JpaRepository<Caja, Long> {
    // Buscar si hay una caja abierta para un usuario (o global si es caja única)
    Optional<Caja> findByEstado(CajaState estado);

    // Si manejas usuarios:
    // Optional<Caja> findByUsuarioAndEstado(String usuario, CajaState estado);
}