package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.Pago;
import com.a.prestamos.model.entity.enums.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoDao extends JpaRepository<Pago, Long> {
    /**
     * Busca todos los pagos de una cuota específica.
     */
    List<Pago> findByInstallmentIdAndPaymentState(Long id, PaymentState paymentState);

    /**
     * Busca todos los pagos de una cuota (cualquier estado).
     */
    List<Pago> findByIdOrderByPaymentDateDesc(Long id);

    /**
     * Busca todos los pagos de un préstamo.
     */
    @Query("SELECT p FROM Pago p WHERE p.installment.loan.id = :prestamoId ORDER BY p.paymentDate DESC")
    List<Pago> findByPrestamoId(@Param("prestamoId") Long prestamoId);

    /**
     * Busca todos los pagos de un cliente por su DNI/RUC.
     */
    @Query("SELECT p FROM Pago p WHERE p.installment.loan.customer.documentId = :documentId ORDER BY p.paymentDate DESC")
    List<Pago> findByClienteDocumentId(@Param("dni") String documentId);

    /**
     * Busca pagos por rango de fecha (para cierre de caja).
     */
    @Query("SELECT p FROM Pago p WHERE p.paymentDate BETWEEN :inicio AND :fin AND p.paymentState = :estado ORDER BY p.paymentDate")
    List<Pago> findByFechaPagoBetweenAndEstado(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("estado") PaymentState estado
    );

    /**
     * Busca un pago por el ID de pago de Mercado Pago.
     */
    Optional<Pago> findByMercadoPagoPaymentId(String paymentId);

    /**
     * Busca un pago por el ID de preferencia de Mercado Pago.
     */
    Optional<Pago> findByMercadoPagoPreferenceId(String preferenceId);

    /**
     * Suma total de pagos activos en un rango de fecha (para cierre de caja).
     */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM Pago p " +
            "WHERE p.paymentDate BETWEEN :inicio AND :fin AND p.paymentState = 'ACTIVO'")
    java.math.BigDecimal sumMontoPagadoByFechaBetween(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    /**
     * ✅ CORREGIDO: Suma los pagos agrupados por método de pago desde una fecha dada.
     * Devuelve: [PaymentMethod, SUM(amountReceived), SUM(change)]
     * - amountReceived: Lo que el cliente entregó físicamente
     * - change: El vuelto que se le devolvió
     * El neto en caja = amountReceived - change
     */
    @Query("SELECT p.paymentMethod, SUM(p.amountReceived), SUM(p.change) " +
            "FROM Pago p " +
            "WHERE p.paymentDate >= :fechaInicio " +
            "AND p.paymentState = 'ACTIVO' " +
            "GROUP BY p.paymentMethod")
    List<Object[]> sumarPagosPorMetodoDesde(@Param("fechaInicio") java.time.Instant fechaInicio);
}