package com.a.prestamos.model.dto.prestamo;

import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.enums.InstallmentState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public record CuotaDto(
        Long id,
        Integer num,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal balance,
        InstallmentState state,

        BigDecimal moraEstimada,
        BigDecimal totalConMora,
        Boolean tieneMora,
        Integer mesesMora,
        Boolean primerMesPerdonado
) {

    private static final BigDecimal TASA_MORA = new BigDecimal("0.01");

    public static CuotaDto fromEntity(Cuota entity) {
        LocalDate hoy = LocalDate.now();

        BigDecimal balance = entity.getBalance() != null ? entity.getBalance() : BigDecimal.ZERO;
        BigDecimal mora = BigDecimal.ZERO;
        int mesesMora = 0;
        boolean primerMesPerdonado = false;
        boolean tieneMora = false;

        // 1. Si no hay saldo o aún no ha vencido -> no hay mora
        if (hoy.isAfter(entity.getDueDate()) && balance.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calcular meses de atraso entre dueDate y hoy (nivel YearMonth)
            YearMonth venc = YearMonth.from(entity.getDueDate());
            YearMonth actual = YearMonth.from(hoy);

            mesesMora = (actual.getYear() - venc.getYear()) * 12
                    + (actual.getMonthValue() - venc.getMonthValue());

            if (mesesMora < 0) {
                mesesMora = 0; // seguridad
            }

            // 3. Ver si hubo algún pago antes del vencimiento
            boolean huboPagoAntesVencimiento = false;
            if (entity.getPayments() != null && !entity.getPayments().isEmpty()) {
                Instant limite = entity.getDueDate()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();

                huboPagoAntesVencimiento = entity.getPayments().stream()
                        .anyMatch(p -> p.getPaymentDate() != null && p.getPaymentDate().isBefore(limite));
            }

            // 4. Si hubo pago antes del vencimiento, perdonamos el primer mes
            if (huboPagoAntesVencimiento && mesesMora > 0) {
                mesesMora -= 1;
                primerMesPerdonado = true;
            }

            if (mesesMora < 0) {
                mesesMora = 0;
            }

            // 5. Calcular mora = saldoPendiente * 1% * mesesMora
            if (mesesMora > 0) {
                mora = balance
                        .multiply(TASA_MORA)
                        .multiply(BigDecimal.valueOf(mesesMora))
                        .setScale(2, RoundingMode.HALF_UP);

                tieneMora = mora.compareTo(BigDecimal.ZERO) > 0;
            }
        }

        BigDecimal totalConMora = balance.add(mora);

        return new CuotaDto(
                entity.getId(),
                entity.getNum(),
                entity.getDueDate(),
                entity.getAmount(),
                entity.getAmountPaid(),
                balance,
                entity.getInstallmentState(),
                mora,
                totalConMora,
                tieneMora,
                mesesMora,
                primerMesPerdonado
        );
    }
}

