package com.a.prestamos.service.impl;

import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Prestamo;
import com.a.prestamos.service.IFinancialService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinancialServiceImpl implements IFinancialService {
    private static final int FINANCIAL_SCALE = 2; // 2 decimales para montos monetarios
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP); // Alta precisión para cálculos
    // intermedios

    // Constantes para IGV (18%)
    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");
    private static final BigDecimal DIVISOR_IGV = new BigDecimal("1.18");

    @Override
    public BigDecimal calculateTem(BigDecimal tea) {
        // Fórmula: TEM = (1 + TEA)^(1/12) - 1
        BigDecimal base = BigDecimal.ONE.add(tea);
        BigDecimal exponent = BigDecimal.ONE.divide(new BigDecimal("12"), MC);
        BigDecimal result = base.pow(exponent.intValue(), MC); // Esta implementación de pow no es ideal para exponentes
        // fraccionarios.
        // Una mejor aproximación es usar raíces o logaritmos.
        // Para simplificar, vamos a usar una aproximación con double.
        double baseDouble = base.doubleValue();
        double exponentDouble = 1.0 / 12.0;
        BigDecimal monthlyFactor = BigDecimal.valueOf(Math.pow(baseDouble, exponentDouble));

        return monthlyFactor.subtract(BigDecimal.ONE);
    }

    @Override
    public BigDecimal calculateInstallmentAmount(BigDecimal principal, BigDecimal tem, int months) {
        // Si la tasa es 0, la cuota es simplemente principal / meses
        if (tem.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(new BigDecimal(months), FINANCIAL_SCALE, ROUNDING_MODE);
        }

        // Fórmula: Cuota = P * [i * (1+i)^n] / [(1+i)^n - 1]
        BigDecimal i = tem;
        BigDecimal n = new BigDecimal(months);

        BigDecimal factorBase = BigDecimal.ONE.add(i);
        BigDecimal factor = factorBase.pow(months, MC); // (1+i)^n

        BigDecimal numerator = i.multiply(factor, MC); // i * (1+i)^n
        BigDecimal denominator = factor.subtract(BigDecimal.ONE); // (1+i)^n - 1

        BigDecimal rateFactor = numerator.divide(denominator, MC);
        return principal.multiply(rateFactor).setScale(FINANCIAL_SCALE, ROUNDING_MODE);
    }

    @Override
    public List<Cuota> generateSchedule(Prestamo prestamo, BigDecimal tem) {
        List<Cuota> schedule = new ArrayList<>();

        BigDecimal currentBalance = prestamo.getPrincipal(); // Saldo inicial
        BigDecimal fixedInstallment = prestamo.getInstallmentAmount(); // Cuota fija

        for (int i = 1; i <= prestamo.getMonths(); i++) {

            // 1. Calcular Interés Global del periodo (Saldo * Tasa)
            BigDecimal interesTotalMes = currentBalance.multiply(tem).setScale(2, RoundingMode.HALF_UP);

            // 2. Desglosar IGV del Interés (SUNAT)
            // Fórmula: Valor Venta = Precio / 1.18
            BigDecimal interesBase = interesTotalMes.divide(DIVISOR_IGV, 2, RoundingMode.HALF_UP);
            BigDecimal igvMes = interesTotalMes.subtract(interesBase);

            // 3. Calcular Amortización de Capital
            // Capital = Cuota Fija - Interés Total
            BigDecimal capitalMes = fixedInstallment.subtract(interesTotalMes);

            // 4. Actualizar Saldo Deudor
            currentBalance = currentBalance.subtract(capitalMes);

            // Ajuste por si el saldo se vuelve negativo por centavos en la última cuota
            if (i == prestamo.getMonths() && currentBalance.compareTo(BigDecimal.ZERO) != 0) {
                // En la ultima cuota, ajustamos el capital para que el saldo quede en 0
                // perfecto
                capitalMes = capitalMes.add(currentBalance);
                currentBalance = BigDecimal.ZERO;
                // Recalculamos la cuota fija final solo para este mes (ajuste de centavos)
                fixedInstallment = capitalMes.add(interesTotalMes);
            }

            // 5. Crear Entidad Cuota
            Cuota installment = new Cuota();
            installment.setLoan(prestamo);
            installment.setNum(i);
            installment.setDueDate(prestamo.getStartDate().plusDays(30L * i));

            // --- DATOS FINANCIEROS CLAVE ---
            installment.setPrincipal(capitalMes); // Va al JSON como Inafecto
            installment.setInterest(interesBase); // Va al JSON como Gravado
            installment.setIgv(igvMes); // Va al JSON como Impuesto
            installment.setAmount(fixedInstallment); // Total a pagar
            // -------------------------------

            installment.setBalance(fixedInstallment); // Inicialmente debe todo
            installment.setAmountPaid(BigDecimal.ZERO);

            schedule.add(installment);
        }
        return schedule;
    }
}
