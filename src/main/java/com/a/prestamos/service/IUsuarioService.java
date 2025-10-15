package com.a.prestamos.service;

import com.a.prestamos.model.entity.Usuario;

import java.util.List;

public interface IUsuarioService {
    //Usuario save(RegisterRequest registerRequest);
    Usuario findById(Long id);
    List<Usuario> findAll();
    Usuario findByEmail(String email);
    Boolean existsByEmail(String email);
    void deleteById(Long id);
}
