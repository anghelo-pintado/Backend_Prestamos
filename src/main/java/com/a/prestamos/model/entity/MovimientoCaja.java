package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.TipoMovimientoCaja;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoCaja tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = 500)
    private String concepto;

    @Column(length = 100)
    private String usuario;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(precision = 12, scale = 2)
    private BigDecimal saldoResultante;

    @PrePersist
    public void prePersist() {
        if (this.fecha == null) {
            this.fecha = Instant.now();
        }
    }
}