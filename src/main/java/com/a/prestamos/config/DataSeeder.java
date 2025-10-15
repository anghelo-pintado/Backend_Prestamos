package com.a.prestamos.config;

import com.a.prestamos.model.dao.UsuarioDao;
import com.a.prestamos.model.entity.Usuario;
import com.a.prestamos.model.entity.enums.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedUsers(UsuarioDao usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.findByEmail("admin@gmail.com").isEmpty()) {
                usuarioRepository.save(Usuario.builder()
                        .email("admin@gmail.com")
                        .password(passwordEncoder.encode("12345678"))
                        .role(Role.ADMIN)
                        .firstname("Edward")
                        .lastname("Castillo")
                        .phone("111111111")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .build());
            }
        };
    }
}
