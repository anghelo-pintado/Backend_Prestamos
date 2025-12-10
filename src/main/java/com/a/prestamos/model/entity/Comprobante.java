package com.a.prestamos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "comprobantes")
public class Comprobante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Pago payment;

    // Datos SUNAT
    @Column(nullable = false, length = 4)
    private String serie; // F001 (Factura), B001 (Boleta)

    @Column(nullable = false)
    private Long sequential; // 1, 2, 3...

    @Column(nullable = false, length = 10)
    private String proofType; // "01" (Factura), "03" (Boleta)

    @Lob  // 👈 AGREGAR - Para texto largo
    @Column(name = "url_xml", columnDefinition = "TEXT")
    private String urlXml;

    @Lob  // 👈 AGREGAR
    @Column(name = "url_cdr", columnDefinition = "TEXT")
    private String urlCdr;

    @Column(name = "url_pdf", length = 500)  // 👈 Este sí puede ser URL
    private String urlPdf;

    @Column(name = "hash", length = 100)
    private String hash;

    @Column(nullable = false, updatable = false)
    private Instant issueDate;

    @PrePersist
    public void prePersist() {
        this.issueDate = Instant.now();
    }
}
