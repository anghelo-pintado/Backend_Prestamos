package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.CajaState;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "cajas")
public class Caja {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoInicial;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalEfectivoSistema = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalDigitalSistema = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal saldoFinalReal;

    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CajaState estado;

    @Column(length = 500)
    private String observaciones;

    // ⭐ NUEVA RELACIÓN: Una caja tiene muchos movimientos
    @OneToMany(mappedBy = "caja", cascade = CascadeType.ALL)
    private List<MovimientoCaja> movimientos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.fechaApertura == null) this.fechaApertura = LocalDateTime.now();
        if (this.estado == null) this.estado = CajaState.ABIERTA;
    }
}