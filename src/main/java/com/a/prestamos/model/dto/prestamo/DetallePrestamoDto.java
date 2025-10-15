package com.a.prestamos.model.dto.prestamo;

import com.a.prestamos.model.dto.cliente.ClienteDto;
import com.a.prestamos.model.entity.Prestamo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record DetallePrestamoDto(
        Long id,
        LocalDate startDate,
        BigDecimal principal,
        BigDecimal teaAnnual,
        Integer months,
        BigDecimal installmentAmount,
        ClienteDto customer,
        List<CuotaDto> schedule
) {
    public static DetallePrestamoDto fromEntity(Prestamo entity) {
        // Carga perezosa de cuotas
        List<CuotaDto> scheduleDto = entity.getInstallments().stream()
                .map(CuotaDto::fromEntity)
                .collect(Collectors.toList());

        return new DetallePrestamoDto(
                entity.getId(),
                entity.getStartDate(),
                entity.getPrincipal(),
                entity.getTeaAnnual(),
                entity.getMonths(),
                entity.getInstallmentAmount(),
                ClienteDto.fromEntity(entity.getCustomer()),
                scheduleDto
        );
    }
}
