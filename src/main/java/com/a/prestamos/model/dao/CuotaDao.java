package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.enums.InstallmentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CuotaDao extends JpaRepository<Cuota, Long> {
    /**
     * Busca todas las cuotas de un préstamo ordenadas por número.
     */
    List<Cuota> findByLoanIdOrderByNumAsc(Long loanId);

    /**
     * Busca cuotas de un préstamo por estado.
     */
    List<Cuota> findByLoanIdAndInstallmentStateOrderByNumAsc(Long loanId, InstallmentState installmentState);

    /**
     * Busca cuotas vencidas (fecha < hoy y no pagadas).
     */
    @Query("SELECT c FROM Cuota c WHERE c.loan.id = :loanId " +
            "AND c.dueDate < :fecha AND c.installmentState != 'PAGADO' ORDER BY c.num ASC")
    List<Cuota> findCuotasVencidas(@Param("loanId") Long loanId, @Param("fecha") LocalDate fecha);

    /**
     * Busca la primera cuota pendiente o vencida de un préstamo.
     */
    @Query("SELECT c FROM Cuota c WHERE c.loan.id = :loanId " +
            "AND c.installmentState IN ('PENDIENTE', 'VENCIDA') ORDER BY c.num ASC")
    List<Cuota> findCuotasPendientesOrVencidas(@Param("loanId") Long loanId);

    /**
     * Obtiene la primera cuota que debe pagarse (la más antigua no pagada).
     */
    @Query("SELECT c FROM Cuota c WHERE c.loan.id = :loanId " +
            "AND c.installmentState != 'PAGADO' ORDER BY c.num ASC LIMIT 1")
    Optional<Cuota> findPrimeraCuotaPendiente(@Param("loanId") Long loanId);

    /**
     * Cuenta cuotas vencidas de un préstamo.
     */
    @Query("SELECT COUNT(c) FROM Cuota c WHERE c.loan.id = :loanId " +
            "AND c.dueDate < :fecha AND c.installmentState != 'PAGADO'")
    Long countCuotasVencidas(@Param("loanId") Long loanId, @Param("fecha") LocalDate fecha);

    /**
     * Suma el saldo pendiente total de un préstamo (deuda total).
     */
    @Query("SELECT COALESCE(SUM(c.balance), 0) FROM Cuota c " +
            "WHERE c.loan.id = :loanId AND c.installmentState != 'PAGADO'")
    BigDecimal sumBalanceByLoanId(@Param("loanId") Long loanId);

    /**
     * Busca cuotas por DNI del cliente.
     */
    @Query("SELECT c FROM Cuota c WHERE c.loan.customer.documentId = :documentId ORDER BY c.num ASC")
    List<Cuota> findByClienteDocumentId(@Param("dni") String documentId);

    /**
     * Verifica si existen cuotas anteriores pendientes.
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cuota c " +
            "WHERE c.loan.id = :loanId AND c.num < :numCuota AND c.installmentState != 'PAGADO'")
    boolean existenCuotasAnterioresPendientes(@Param("loanId") Long loanId, @Param("numCuota") Integer numCuota);
}
