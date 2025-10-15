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
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP); // Alta precisión para cálculos intermedios


    @Override
    public BigDecimal calculateTem(BigDecimal tea) {
        // Fórmula: TEM = (1 + TEA)^(1/12) - 1
        BigDecimal base = BigDecimal.ONE.add(tea);
        BigDecimal exponent = BigDecimal.ONE.divide(new BigDecimal("12"), MC);
        BigDecimal result = base.pow(exponent.intValue(), MC); // Esta implementación de pow no es ideal para exponentes fraccionarios.
        // Una mejor aproximación es usar raíces o logaritmos.
        // Para simplificar, vamos a usar una aproximación con double.
        double baseDouble = base.doubleValue();
        double exponentDouble = 1.0/12.0;
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
//        BigDecimal currentBalance = prestamo.getPrincipal();
        BigDecimal fixedInstallment = prestamo.getInstallmentAmount();

        for (int i = 1; i <= prestamo.getMonths(); i++) {
            Cuota installment = new Cuota();
            installment.setLoan(prestamo);
            installment.setNum(i);
            installment.setDueDate(prestamo.getStartDate().plusDays(30L * i));
            installment.setAmount(fixedInstallment);

            // Cálculo de interés para la cuota actual
//            BigDecimal interest = currentBalance.multiply(tem).setScale(FINANCIAL_SCALE, ROUNDING_MODE);
//            installment.setInterest(interest);

            // Ajuste para la última cuota
//            if (i == prestamo.getMonths()) {
//                BigDecimal principalForLastInstallment = currentBalance;
//                installment.setPrincipal(principalForLastInstallment);
//                installment.setAmount(principalForLastInstallment.add(interest));
//                installment.setBalance(BigDecimal.ZERO.setScale(FINANCIAL_SCALE));
//            } else {
//                BigDecimal principalForInstallment = fixedInstallment.subtract(interest);
//                installment.setPrincipal(principalForInstallment);
//                installment.setAmount(fixedInstallment);
//                currentBalance = currentBalance.subtract(principalForInstallment);
//                installment.setBalance(currentBalance);
//            }

            schedule.add(installment);
        }
        return schedule;
    }
}
