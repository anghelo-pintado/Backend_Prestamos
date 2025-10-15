package com.a.prestamos.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

//    @Column(nullable = false, precision = 12, scale = 2)
//    private BigDecimal interest;
//
//    @Column(nullable = false, precision = 12, scale = 2)
//    private BigDecimal principal;
//
//    @Column(nullable = false, precision = 12, scale = 2)
//    private BigDecimal balance;
}
