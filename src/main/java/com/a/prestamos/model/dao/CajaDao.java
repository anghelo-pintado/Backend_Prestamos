package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.model.entity.enums.CajaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CajaDao extends JpaRepository<Caja, Long> {

    @Query("SELECT c FROM Caja c WHERE c.estado = :estado ORDER BY c.id DESC")
    Optional<Caja> findByEstado(@Param("estado") CajaState estado);

    @Query(value = "SELECT * FROM cajas WHERE estado = 'ABIERTA' ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<Caja> findCajaAbiertaNativa();

    // ✅ CORREGIDO: Query compatible con H2 usando rangos de fecha
    @Query("SELECT c FROM Caja c WHERE c.fechaApertura >= :inicioDelDia AND c.fechaApertura < :finDelDia ORDER BY c.id DESC")
    Optional<Caja> findByFechaApertura(
            @Param("inicioDelDia") LocalDateTime inicioDelDia,
            @Param("finDelDia") LocalDateTime finDelDia
    );
}