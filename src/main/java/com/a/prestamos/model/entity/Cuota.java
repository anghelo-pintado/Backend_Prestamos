package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.InstallmentState;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "cuotas")
public class Cuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Prestamo loan;

    @Column(nullable = false)
    private Integer num;

    @Column(nullable = false)
    private LocalDate dueDate;

    // --- DESGLOSE SUNAT ---
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principal; // Capital (No grava IGV)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal interest;  // Interés (Base Imponible)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;       // Impuesto (18% del interés)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    // ----------------------------------

    /**
     * Monto total pagado hasta el momento para esta cuota.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /**
     * Saldo pendiente de pago para esta cuota.
     * Se inicializa con el monto total y se reduce con cada pago.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentState installmentState = InstallmentState.PENDIENTE; // PENDIENTE, PAGADO, VENCIDO

    /**
     * Lista de pagos asociados a esta cuota.
     */
    @OneToMany(mappedBy = "installment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pago> payments = new ArrayList<>();

    /**
     * Inicializa el saldo pendiente con el monto de la cuota.
     * Se llama automáticamente antes de persistir.
     */
    @PrePersist
    public void prePersist() {
        if (this.balance == null) {
            this.balance = this.amount;
        }
        if (this.amountPaid == null) {
            this.amountPaid = BigDecimal.ZERO;
        }
    }
}
