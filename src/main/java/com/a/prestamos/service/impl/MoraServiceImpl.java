package com.a.prestamos.service.impl;

import com.a.prestamos.model.dto.mora.ResultadoMora;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.service.IMoraService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class MoraServiceImpl implements IMoraService {

    // Tasa mensual del 1% (0.01)
    private static final BigDecimal TASA_MORA = new BigDecimal("0.01");

    @Override
    public ResultadoMora calcularDistribucionMora(Cuota cuota, BigDecimal montoPagado) {
        BigDecimal moraCalculada = BigDecimal.ZERO;
        LocalDate hoy = LocalDate.now();

        // Verificamos si la fecha actual es posterior a la fecha de vencimiento
        boolean cuotaVencida = hoy.isAfter(cuota.getDueDate());

        if (cuotaVencida) {
            // 1. Calcular la diferencia de meses (Septiembre a Diciembre = 3 meses)
            long mesesRetraso = ChronoUnit.MONTHS.between(cuota.getDueDate(), hoy);

            // Regla de negocio: Si está vencida, al menos cobramos 1 mes de mora
            // aunque no haya pasado un mes completo cronológico desde el vencimiento.
            if (mesesRetraso < 1) {
                mesesRetraso = 1;
            }

            // 2. Calcular Mora: SaldoPendiente * 0.01 * CantidadMeses
            // Ejemplo: 3.07 * 0.01 = 0.0307 -> 0.0307 * 3 = 0.0921 -> Redondeado: 0.09
            BigDecimal moraMensual = cuota.getBalance().multiply(TASA_MORA);
            moraCalculada = moraMensual.multiply(new BigDecimal(mesesRetraso))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Deuda Total = Capital Pendiente + Mora Calculada
        BigDecimal deudaTotalExigible = cuota.getBalance().add(moraCalculada);

        // 3. Validación: El pago no puede exceder la deuda total (con una pequeña tolerancia por redondeo)
        // Usamos una tolerancia de 0.00 para ser estrictos, pero el mensaje de error ahora es descriptivo.
        if (montoPagado.compareTo(deudaTotalExigible) > 0) {
            throw new IllegalArgumentException(
                    String.format("El monto a pagar (S/ %s) no puede ser mayor a la deuda total (S/ %s).",
                            montoPagado, deudaTotalExigible)
            );
        }

        // 4. Distribuir el pago entre Mora y Capital
        BigDecimal moraACobrar;
        BigDecimal capitalAmortizado;
        boolean moraPerdonada;

        if (cuotaVencida) {
            // Si está vencida, la mora es obligatoria (no perdonada)
            moraPerdonada = false;

            // Lógica de llenado: Primero se paga la mora, lo que sobra va al capital
            if (montoPagado.compareTo(moraCalculada) <= 0) {
                // Si paga poco, todo va a mora
                moraACobrar = montoPagado;
                capitalAmortizado = BigDecimal.ZERO;
            } else {
                // Si paga suficiente, cubrimos toda la mora y el resto a capital
                moraACobrar = moraCalculada;
                capitalAmortizado = montoPagado.subtract(moraACobrar);
            }
        } else {
            // Si NO está vencida (pago puntual o adelantado)
            moraPerdonada = true; // (Técnicamente es 0, pero marcamos como perdonada/inexistente)
            moraACobrar = BigDecimal.ZERO;
            capitalAmortizado = montoPagado;
        }

        return new ResultadoMora(moraCalculada, moraACobrar, capitalAmortizado, moraPerdonada);
    }
}