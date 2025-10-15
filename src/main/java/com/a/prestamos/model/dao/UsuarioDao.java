package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioDao extends JpaRepository<Usuario, Long> {
    Boolean existsByEmail(String email);
    Optional<Usuario> findByEmail(String email);
}
