package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.DocumentType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @Column(length = 15) // Aumentamos longitud para soportar RUC (11 dígitos)
    private String documentId; // Antes 'documentId'. Ahora guardará DNI o RUC.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType; // ENUM: DNI, RUC

    @Column(nullable = false)
    private String fullName;

    private String address; // OBLIGATORIO para Facturas (RUC)

    private String firstName;
    private String firstLastName;
    private String secondLastName;

    @Column(nullable = false)
    private boolean pep = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
