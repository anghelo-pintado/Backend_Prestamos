package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.PaymentMethod;
import com.a.prestamos.model.entity.enums.PaymentState;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pagos")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", nullable = false)
    private Cuota installment;

    // --- RELACIÓN CON COMPROBANTE ---
    // Un pago genera UN comprobante (Factura o Boleta)
    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
    private Comprobante proofPayment;
    // --------------------------------

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid; // Lo que reduce la deuda

    // --- CAMPOS EFECTIVO ---
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountReceived; // Lo que el cliente entregó

    @Column(precision = 12, scale = 2)
    private BigDecimal change = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rounding = BigDecimal.ZERO; // Diferencia por redondeo (Solo efectivo)

    // --- CAMPOS MORA ---
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montMora = BigDecimal.ZERO;
    @Column(nullable = false)
    private Boolean moraPerdonada = false;

    // --- METODOS Y ESTADO ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, updatable = false)
    private Instant paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentState paymentState = PaymentState.ACTIVO;

    // --- TRAZABILIDAD ---
    @Column(length = 50)
    private String operationTrace; // Código de operación de Yape/Plin o Voucher Tarjeta

    @Column(length = 100)
    private String mercadoPagoPreferenceId;

    @Column(length = 100)
    private String mercadoPagoPaymentId;

    @Column(length = 500)
    private String observations;

    // Auditoria...
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.paymentDate == null) this.paymentDate = Instant.now();
        this.createdAt = Instant.now();
    }
}
