package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteDao extends JpaRepository<Cliente, String> {
}
