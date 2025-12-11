package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.CajaState;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cajas")
public class Caja {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Podrías vincularlo a un Usuario entity si tienes autenticación
    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    // Dinero físico con el que inicia el día
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoInicial;

    // Total Efectivo calculado por el sistema (Ventas)
    @Column(precision = 12, scale = 2)
    private BigDecimal totalEfectivoSistema = BigDecimal.ZERO;

    // Total Digital (Yape/Plin/Tarjeta) calculado por el sistema
    @Column(precision = 12, scale = 2)
    private BigDecimal totalDigitalSistema = BigDecimal.ZERO;

    // Lo que el cajero cuenta físicamente al final
    @Column(precision = 12, scale = 2)
    private BigDecimal saldoFinalReal;

    // Diferencia (Debe ser 0.00 para permitir cierre)
    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CajaState estado; // Crear enum: ABIERTA, CERRADA

    private String observaciones; // Opcional para el cajero al cerrar

    @PrePersist
    public void prePersist() {
        if (this.fechaApertura == null) this.fechaApertura = LocalDateTime.now();
        if (this.estado == null) this.estado = CajaState.ABIERTA;
    }
}
