package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.CajaDao;
import com.a.prestamos.model.dao.PagoDao;
import com.a.prestamos.model.dto.caja.AperturaCajaRequest;
import com.a.prestamos.model.dto.caja.CierreCajaRequest;
import com.a.prestamos.model.dto.caja.ResumenCajaDto;
import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.model.entity.enums.CajaState;
import com.a.prestamos.model.entity.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId; // Para convertir LocalDateTime a Instant si es necesario
import java.util.List;

@Service
@RequiredArgsConstructor
public class CajaServiceImpl {

    private final CajaDao cajaDao;
    private final PagoDao pagoDao;

    @Transactional
    public Caja abrirCaja(AperturaCajaRequest req) {
        // 1. Validar que no haya caja abierta
        if (cajaDao.findByEstado(CajaState.ABIERTA).isPresent()) {
            throw new IllegalStateException("Ya existe una caja abierta. Debe cerrarla primero.");
        }

        // 2. Crear caja
        Caja caja = new Caja();
        caja.setUsuario(req.usuario() != null ? req.usuario() : "CajeroPrincipal");
        caja.setSaldoInicial(req.saldoInicial());
        caja.setEstado(CajaState.ABIERTA);

        return cajaDao.save(caja);
    }

    @Transactional(readOnly = true)
    public ResumenCajaDto obtenerResumenActual() {
        Caja caja = cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("No hay caja abierta."));

        // Calcular totales desde el DAO
        TotalesCaja totales = calcularTotales(caja);

        BigDecimal totalEsperadoEnCaja = caja.getSaldoInicial().add(totales.totalEfectivo);

        return new ResumenCajaDto(
                caja.getId(),
                caja.getFechaApertura().toString(),
                caja.getSaldoInicial(),
                totales.totalEfectivo,
                totales.totalDigital,
                totalEsperadoEnCaja
        );
    }

    @Transactional
    public Caja cerrarCaja(CierreCajaRequest req) {
        // 1. Obtener caja
        Caja caja = cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("No hay caja abierta para cerrar."));

        // 2. Calcular lo que "Debería" haber (Sistema)
        TotalesCaja totales = calcularTotales(caja);

        BigDecimal saldoInicial = caja.getSaldoInicial();
        BigDecimal efectivoSistema = totales.totalEfectivo; // Incluye redondeo

        // El sistema espera: Saldo Inicial + Ventas Efectivo
        BigDecimal saldoEsperado = saldoInicial.add(efectivoSistema);

        // 3. Comparar con lo que el usuario contó (Monto Físico)
        BigDecimal montoFisico = req.montoFisico();
        BigDecimal diferencia = montoFisico.subtract(saldoEsperado);

        // LÓGICA DE INCIDENCIA
        if (diferencia.compareTo(BigDecimal.ZERO) != 0) {
            // Si hay diferencia y NO ha confirmado el descuadre, lanzamos error (alerta al usuario)
            if (!req.confirmarDescuadre()) {
                String tipo = diferencia.compareTo(BigDecimal.ZERO) > 0 ? "sobrante" : "faltante";
                throw new IllegalStateException(
                        String.format("⚠️ DESCUADRE: Hay un %s de S/ %s. Sistema: S/ %s vs Físico: S/ %s. " +
                                        "Marque la casilla de 'Confirmar Descuadre' para proceder.",
                                tipo, diferencia.abs(), saldoEsperado, req.montoFisico())
                );
            }
            // Si SÍ confirmó, guardamos la observación (incidencia)
            caja.setObservaciones(req.observaciones()); // Asegúrate de tener este campo en la Entidad Caja
        }

        // 5. Si cuadra (diferencia 0), procedemos al cierre
        caja.setFechaCierre(LocalDateTime.now());
        caja.setTotalEfectivoSistema(efectivoSistema);
        caja.setTotalDigitalSistema(totales.totalDigital);
        caja.setSaldoFinalReal(montoFisico);
        caja.setDiferencia(diferencia); // Será 0.00
        caja.setEstado(CajaState.CERRADA);

        return cajaDao.save(caja);
    }

    // --- Helper para calcular sumas ---
    private TotalesCaja calcularTotales(Caja caja) {
        // Convertir LocalDateTime a Instant porque tu Pago usa Instant
        java.time.Instant inicio = caja.getFechaApertura().atZone(ZoneId.systemDefault()).toInstant();

        List<Object[]> resultados = pagoDao.sumarPagosPorMetodoDesde(inicio);

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalDigital = BigDecimal.ZERO;

        for (Object[] row : resultados) {
            PaymentMethod metodo = (PaymentMethod) row[0];
            BigDecimal montoPagado = (BigDecimal) row[1];
            BigDecimal redondeo = (BigDecimal) row[2]; // Puede ser null o 0
            if (redondeo == null) redondeo = BigDecimal.ZERO;

            if (metodo == PaymentMethod.EFECTIVO) {
                // Efectivo en caja = Lo que pagó de deuda + el redondeo que se quedó
                // Ejemplo: Deuda 9.90, paga con 10.00. Pagado=9.90, Redondeo=0.10. Total Caja=10.00
                totalEfectivo = totalEfectivo.add(montoPagado).add(redondeo);
            } else {
                // Digital: Solo importa el monto pagado
                totalDigital = totalDigital.add(montoPagado);
            }
        }
        return new TotalesCaja(totalEfectivo, totalDigital);
    }

    // Record interno simple para pasar datos
    private record TotalesCaja(BigDecimal totalEfectivo, BigDecimal totalDigital) {}
}