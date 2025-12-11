package com.a.prestamos.service.impl;

import com.a.prestamos.model.dto.mora.ResultadoMora;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.service.IMoraService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;


@Service
public class MoraServiceImpl implements IMoraService {

    private static final BigDecimal TASA_MORA = new BigDecimal("0.01");

    @Override
    public ResultadoMora calcularDistribucionMora(Cuota cuota, BigDecimal montoPagado) {
        BigDecimal moraCalculada = BigDecimal.ZERO;

        boolean cuotaVencida = LocalDate.now().isAfter(cuota.getDueDate());

        if (cuotaVencida) {
            moraCalculada = cuota.getBalance()
                    .multiply(TASA_MORA)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal deudaTotalExigible = cuota.getBalance().add(moraCalculada);

        if (montoPagado.compareTo(deudaTotalExigible) > 0) {
            throw new IllegalArgumentException("El monto no puede ser mayor a la deuda total.");
        }

        BigDecimal moraACobrar;
        BigDecimal capitalAmortizado;
        boolean moraPerdonada;

        if (cuotaVencida) {
            // Cuota vencida: no se perdona mora
            moraPerdonada = false;

            if (montoPagado.compareTo(moraCalculada) <= 0) {
                moraACobrar = montoPagado;
                capitalAmortizado = BigDecimal.ZERO;
            } else {
                moraACobrar = moraCalculada;
                capitalAmortizado = montoPagado.subtract(moraACobrar);
            }
        } else {
            // Cuota al día: se perdona la mora
            moraPerdonada = true;
            moraACobrar = BigDecimal.ZERO;
            capitalAmortizado = montoPagado;
        }

        return new ResultadoMora(moraCalculada, moraACobrar, capitalAmortizado, moraPerdonada);
    }
}
