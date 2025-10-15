package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Cuota;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuotaDao extends JpaRepository<Cuota, Long> {
}
