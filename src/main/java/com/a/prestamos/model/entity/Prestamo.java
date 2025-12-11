package com.a.prestamos.model.entity;

import com.a.prestamos.model.entity.enums.LoanState;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "prestamos")
public class Prestamo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_dni", nullable = false)
    private Cliente customer;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal teaAnnual;

    @Column(nullable = false)
    private Integer months;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal installmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoanState loanState = LoanState.ACTIVO;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Cuota> installments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
