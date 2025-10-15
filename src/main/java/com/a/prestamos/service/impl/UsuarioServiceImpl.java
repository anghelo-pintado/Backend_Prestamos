package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.UsuarioDao;
import com.a.prestamos.model.entity.Usuario;
import com.a.prestamos.service.IUsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class UsuarioServiceImpl implements IUsuarioService {
    @Autowired
    private UsuarioDao usuarioDao;

//    @Transactional
//    @Override
//    public Usuario save(RegisterRequest registerRequest) {
//        Usuario usuario = Usuario.builder()
//                .firstname(registerRequest.getFirstname())
//                .lastname(registerRequest.getLastname())
//                .email(registerRequest.getEmail())
//                .phone(registerRequest.getPhone())
//                .birthDate(LocalDate.parse(registerRequest.getBirthdate()))
//                .password(registerRequest.getPassword())
//                .role(Role.CIUDADANO)
//                .build();
//        return usuarioDao.save(usuario);
//    }

    @Transactional(readOnly = true)
    @Override
    public Usuario findById(Long id) {
        return usuarioDao.findById(id).orElse(null);
    }

    @Override
    public List<Usuario> findAll() {
        return usuarioDao.findAll();
    }

    @Transactional(readOnly = true)
    @Override
    public Usuario findByEmail(String email) {
        return usuarioDao.findByEmail(email).orElse(null);
    }

    @Override
    public Boolean existsByEmail(String email) {
        return usuarioDao.existsByEmail(email);
    }

    @Override
    public void deleteById(Long id) {
        usuarioDao.deleteById(id);
    }
}
