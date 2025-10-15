package com.a.prestamos.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @Column(length = 8)
    private String dni;

    @Column(nullable = false)
    private String fullName;

    private String firstName;
    private String firstLastName;
    private String secondLastName;

    @Column(nullable = false)
    private boolean pep = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
