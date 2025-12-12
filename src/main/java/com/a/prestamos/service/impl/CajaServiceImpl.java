package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.CajaDao;
import com.a.prestamos.model.dao.MovimientoCajaDao;
import com.a.prestamos.model.dao.PagoDao;
import com.a.prestamos.model.dto.caja.*;
import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.model.entity.MovimientoCaja;
import com.a.prestamos.model.entity.enums.CajaState;
import com.a.prestamos.model.entity.enums.PaymentMethod;
import com.a.prestamos.model.entity.enums.TipoMovimientoCaja;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CajaServiceImpl {

    private final CajaDao cajaDao;
    private final PagoDao pagoDao;
    private final MovimientoCajaDao movimientoCajaDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Caja abrirCaja(AperturaCajaRequest req) {
        entityManager.flush();
        entityManager.clear();

        // Validaciones
        if (cajaDao.findByEstado(CajaState.ABIERTA).isPresent()) {
            throw new IllegalStateException("Ya existe una caja abierta. Debe cerrarla primero.");
        }

        LocalDateTime hoy = LocalDateTime.now();
        LocalDateTime inicioDia = hoy.toLocalDate().atStartOfDay();
        LocalDateTime finDia = inicioDia.plusDays(1);

        Optional<Caja> cajaDelDia = cajaDao.findByFechaApertura(inicioDia, finDia);

        if (cajaDelDia.isPresent()) {
            Caja cajaExistente = cajaDelDia.get();
            String estado = cajaExistente.getEstado() == CajaState.CERRADA ? "cerrada" : "en proceso";
            throw new IllegalStateException(
                    String.format("⚠️ YA SE ABRIÓ CAJA HOY (%s). La caja del día está %s.",
                            cajaExistente.getFechaApertura().toLocalDate(), estado)
            );
        }

        Caja caja = new Caja();
        caja.setUsuario(req.usuario() != null ? req.usuario() : "CajeroPrincipal");
        caja.setSaldoInicial(req.saldoInicial());
        caja.setEstado(CajaState.ABIERTA);

        log.info("✅ Nueva caja abierta para el día: {}", hoy.toLocalDate());
        return cajaDao.save(caja);
    }

    @Transactional(readOnly = true)
    public ResumenCajaDto obtenerResumenActual() {
        Caja caja = obtenerCajaAbierta();

        TotalesCaja totales = calcularTotales(caja);

        // ✅ USAMOS EL NUEVO MÉTODO GENÉRICO
        BigDecimal totalReingresos = movimientoCajaDao.calcularTotalPorTipo(caja.getId(), TipoMovimientoCaja.REINGRESO);
        BigDecimal totalVueltos = movimientoCajaDao.calcularTotalPorTipo(caja.getId(), TipoMovimientoCaja.PAGO_VUELTO);

        BigDecimal efectivoDisponible = calcularEfectivoDisponible(caja.getId());
        BigDecimal totalEsperadoEnCaja = caja.getSaldoInicial()
                .add(totales.totalEfectivo)
                .add(totalReingresos)
                .subtract(totalVueltos);

        return new ResumenCajaDto(
                caja.getId(),
                caja.getFechaApertura().toString(),
                caja.getSaldoInicial(),
                totales.totalEfectivo,
                totales.totalDigital,
                totalReingresos,
                totalVueltos,
                totalEsperadoEnCaja,
                efectivoDisponible
        );
    }

    @Transactional
    public Caja cerrarCaja(CierreCajaRequest req) {
        Caja caja = obtenerCajaAbierta();

        TotalesCaja totales = calcularTotales(caja);
        BigDecimal saldoInicial = caja.getSaldoInicial();
        BigDecimal efectivoSistema = totales.totalEfectivo;

        // ✅ USAMOS EL NUEVO MÉTODO GENÉRICO
        BigDecimal reingresos = movimientoCajaDao.calcularTotalPorTipo(caja.getId(), TipoMovimientoCaja.REINGRESO);
        BigDecimal vueltos = movimientoCajaDao.calcularTotalPorTipo(caja.getId(), TipoMovimientoCaja.PAGO_VUELTO);

        BigDecimal saldoEsperado = saldoInicial
                .add(efectivoSistema)
                .add(reingresos)
                .subtract(vueltos);

        BigDecimal montoFisico = req.montoFisico();
        BigDecimal diferencia = montoFisico.subtract(saldoEsperado);

        if (diferencia.compareTo(BigDecimal.ZERO) != 0) {
            if (!req.confirmarDescuadre()) {
                String tipo = diferencia.compareTo(BigDecimal.ZERO) > 0 ? "sobrante" : "faltante";
                throw new IllegalStateException(
                        String.format("⚠️ DESCUADRE: Hay un %s de S/ %s. Sistema: S/ %s vs Físico: S/ %s.",
                                tipo, diferencia.abs(), saldoEsperado, req.montoFisico())
                );
            }
            caja.setObservaciones(req.observaciones());
        }

        caja.setFechaCierre(LocalDateTime.now());
        caja.setTotalEfectivoSistema(efectivoSistema);
        caja.setTotalDigitalSistema(totales.totalDigital);
        caja.setSaldoFinalReal(montoFisico);
        caja.setDiferencia(diferencia);
        caja.setEstado(CajaState.CERRADA);

        log.info("✅ Caja cerrada. Diferencia: S/ {}", diferencia);
        return cajaDao.save(caja);
    }

    @Transactional
    public ReingresoCajaResponse registrarReingreso(ReingresoCajaRequest request) {
        Caja caja = obtenerCajaAbierta();
        BigDecimal efectivoAntes = calcularEfectivoDisponible(caja.getId());

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(TipoMovimientoCaja.REINGRESO);
        movimiento.setMonto(request.monto());
        movimiento.setConcepto(request.concepto() != null ? request.concepto() : "Reingreso de efectivo");
        movimiento.setUsuario(request.usuario() != null ? request.usuario() : "Sistema");

        // Sumamos porque es reingreso
        movimiento.setSaldoResultante(efectivoAntes.add(request.monto()));

        MovimientoCaja guardado = movimientoCajaDao.save(movimiento);
        log.info("✅ Reingreso registrado: S/ {}", request.monto());

        return ReingresoCajaResponse.fromEntity(guardado, movimiento.getSaldoResultante());
    }

    @Transactional
    public void registrarVuelto(Long cajaId, BigDecimal montoVuelto, Long pagoId) {
        Caja caja = cajaDao.findById(cajaId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada"));

        BigDecimal efectivoAntes = calcularEfectivoDisponible(cajaId);

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(TipoMovimientoCaja.PAGO_VUELTO);
        movimiento.setMonto(montoVuelto);
        movimiento.setConcepto("Vuelto entregado - Pago ID: " + pagoId);
        movimiento.setUsuario("Sistema");

        // Restamos porque es vuelto
        movimiento.setSaldoResultante(efectivoAntes.subtract(montoVuelto));

        movimientoCajaDao.save(movimiento);
        log.debug("✅ Vuelto registrado: S/ {}", montoVuelto);
    }

    @Transactional
    public void registrarPago(Long cajaId, BigDecimal montoRecibido, PaymentMethod metodo, Long pagoId, Integer numeroCuota) {
        if (metodo != PaymentMethod.EFECTIVO) return;

        Caja caja = cajaDao.findById(cajaId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada"));

        // El pago ya existe en DB, así que calcularEfectivoDisponible YA lo incluye
        BigDecimal efectivoActualConPago = calcularEfectivoDisponible(cajaId);

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(TipoMovimientoCaja.PAGO);
        movimiento.setMonto(montoRecibido);
        movimiento.setConcepto(String.format("Pago cuota #%d - EFECTIVO - Pago ID: %d", numeroCuota, pagoId));
        movimiento.setUsuario("Sistema");

        // ✅ CORRECCIÓN SALDO: Usamos el saldo calculado directamenta (NO sumamos de nuevo)
        movimiento.setSaldoResultante(efectivoActualConPago);

        movimientoCajaDao.save(movimiento);
        log.debug("✅ Pago registrado con movimiento. Saldo: S/ {}", movimiento.getSaldoResultante());
    }

    @Transactional(readOnly = true)
    public ValidacionVueltoResponse validarEfectivoParaVuelto(BigDecimal vueltoRequerido) {
        Caja caja = obtenerCajaAbierta();
        BigDecimal efectivoDisponible = calcularEfectivoDisponible(caja.getId());
        return ValidacionVueltoResponse.crear(efectivoDisponible, vueltoRequerido);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularEfectivoDisponible(Long cajaId) {
        Caja caja = cajaDao.findById(cajaId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada"));

        BigDecimal efectivoDisponible = caja.getSaldoInicial();

        TotalesCaja totales = calcularTotales(caja);
        efectivoDisponible = efectivoDisponible.add(totales.totalEfectivo);

        // ✅ USAMOS EL NUEVO MÉTODO GENÉRICO
        BigDecimal reingresos = movimientoCajaDao.calcularTotalPorTipo(cajaId, TipoMovimientoCaja.REINGRESO);
        efectivoDisponible = efectivoDisponible.add(reingresos);

        BigDecimal vueltos = movimientoCajaDao.calcularTotalPorTipo(cajaId, TipoMovimientoCaja.PAGO_VUELTO);
        efectivoDisponible = efectivoDisponible.subtract(vueltos);

        return efectivoDisponible;
    }

    @Transactional(readOnly = true)
    public List<MovimientoCajaDto> obtenerMovimientos() {
        Caja caja = obtenerCajaAbierta();
        return movimientoCajaDao.findByCajaIdOrderByFechaDesc(caja.getId())
                .stream()
                .map(MovimientoCajaDto::fromEntity)
                .collect(Collectors.toList());
    }

    private Caja obtenerCajaAbierta() {
        entityManager.flush();
        entityManager.clear();
        Optional<Caja> cajaOpt = cajaDao.findByEstado(CajaState.ABIERTA);

        if (cajaOpt.isEmpty()) {
            cajaOpt = cajaDao.findCajaAbiertaNativa();
        }
        return cajaOpt.orElseThrow(() -> new IllegalStateException("No hay caja abierta."));
    }

    private TotalesCaja calcularTotales(Caja caja) {
        Instant inicio = caja.getFechaApertura().atZone(ZoneId.systemDefault()).toInstant();
        List<Object[]> resultados = pagoDao.sumarPagosPorMetodoDesde(inicio);

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalDigital = BigDecimal.ZERO;

        for (Object[] row : resultados) {
            PaymentMethod metodo = (PaymentMethod) row[0];
            BigDecimal montoRecibido = (BigDecimal) row[1];

            if (metodo == PaymentMethod.EFECTIVO) {
                totalEfectivo = totalEfectivo.add(montoRecibido);
            } else {
                totalDigital = totalDigital.add(montoRecibido);
            }
        }
        return new TotalesCaja(totalEfectivo, totalDigital);
    }

    private record TotalesCaja(BigDecimal totalEfectivo, BigDecimal totalDigital) {}
}