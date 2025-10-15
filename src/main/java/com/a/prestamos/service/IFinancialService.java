package com.a.prestamos.service;

import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Prestamo;

import java.math.BigDecimal;
import java.util.List;

public interface IFinancialService {

    /**
     * Calcula la Tasa Efectiva Mensual (TEM) a partir de una Tasa Efectiva Anual (TEA).
     *
     * @param tea La TEA como un BigDecimal (ej. 0.10 para 10%).
     * @return La TEM calculada.
     */
    BigDecimal calculateTem(BigDecimal tea);

    /**
     * Calcula el monto de la cuota fija mensual usando la fórmula de anualidad.
     *
     * @param principal El monto principal del préstamo.
     * @param tem La Tasa Efectiva Mensual.
     * @param months El plazo en meses.
     * @return El monto de la cuota fija.
     */
    BigDecimal calculateInstallmentAmount(BigDecimal principal, BigDecimal tem, int months);

    /**
     * Genera el cronograma de pagos completo para un préstamo.
     *
     * @param prestamo El préstamo para el cual se generará el cronograma.
     * @param tem La Tasa Efectiva Mensual.
     * @return Una lista de todas las cuotas (Installment).
     */
    List<Cuota> generateSchedule(Prestamo prestamo, BigDecimal tem);
}
