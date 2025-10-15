package com.a.prestamos.model.dto.prestamo;

import com.a.prestamos.model.entity.Cuota;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaDto(
        Integer num,
        LocalDate dueDate,
        BigDecimal amount
//        BigDecimal interest,
//        BigDecimal principal,
//        BigDecimal balance
) {
    public static CuotaDto fromEntity(Cuota entity) {
        return new CuotaDto(
                entity.getNum(),
                entity.getDueDate(),
                entity.getAmount()
//                entity.getInterest(),
//                entity.getPrincipal(),
//                entity.getBalance()
        );
    }
}
