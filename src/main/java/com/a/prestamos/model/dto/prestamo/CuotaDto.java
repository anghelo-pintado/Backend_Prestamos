package com.a.prestamos.model.dto.prestamo;

import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.enums.InstallmentState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record CuotaDto(
        Long id,
        Integer num,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal balance,
        InstallmentState state,

        // --- NUEVOS CAMPOS CALCULADOS ---
        BigDecimal moraEstimada, // 1% si aplica
        BigDecimal totalConMora, // balance + mora
        Boolean tieneMora        // true si está vencida
) {
    public static CuotaDto fromEntity(Cuota entity) {
        // LÓGICA DE VISUALIZACIÓN DE MORA
        BigDecimal mora = BigDecimal.ZERO;

        // Regla: Si hoy es mayor al vencimiento Y aún debe dinero
        if (LocalDate.now().isAfter(entity.getDueDate()) &&
                entity.getBalance().compareTo(BigDecimal.ZERO) > 0) {

            // 1% del saldo pendiente
            mora = entity.getBalance().multiply(new BigDecimal("0.01"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // El total sugerido a pagar hoy
        BigDecimal total = entity.getBalance().add(mora);

        return new CuotaDto(
                entity.getId(),
                entity.getNum(),
                entity.getDueDate(),
                entity.getAmount(),
                entity.getAmountPaid(),
                entity.getBalance(),
                entity.getInstallmentState(),
                // Nuevos valores
                mora,
                total,
                mora.compareTo(BigDecimal.ZERO) > 0
        );
    }
}
