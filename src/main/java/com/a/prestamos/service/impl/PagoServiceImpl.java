package com.a.prestamos.service.impl;

import com.a.prestamos.exception.prestamo.ResourceNotFoundException;
import com.a.prestamos.model.dao.CajaDao;
import com.a.prestamos.model.dao.CuotaDao;
import com.a.prestamos.model.dao.PagoDao;
import com.a.prestamos.model.dao.PrestamoDao;
import com.a.prestamos.model.dto.mercadoPago.MercadoPagoPreferenceResponse;
import com.a.prestamos.model.dto.mora.ResultadoMora;
import com.a.prestamos.model.dto.pago.PagoRequest;
import com.a.prestamos.model.dto.pago.PagoResponse;
import com.a.prestamos.model.entity.Caja;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Pago;
import com.a.prestamos.model.entity.Prestamo;
import com.a.prestamos.model.entity.enums.*;
import com.a.prestamos.service.IMoraService;
import com.a.prestamos.service.IPagoService;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoServiceImpl implements IPagoService {

    private final PagoDao pagoDao;
    private final CuotaDao cuotaDao;
    private final PrestamoDao prestamoDao;

    private final IMoraService moraService;
    private final CajaServiceImpl cajaService;
    private final FacturacionServiceImpl facturacionService;
    private final CajaDao cajaDao;

    @Value("${app.mercado-pago.access-token}")
    private String mercadoPagoAccessToken;

    @Value("${app.mercado-pago.webhook-url}")
    private String webhookUrl;

    @Value("${app.mercado-pago.back-url-base}")
    private String backUrlBase;

    @Override
    @Transactional
    public PagoResponse registrarPago(PagoRequest request) {

        // Validar Caja Abierta
        Caja cajaActual = cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("⛔ NO SE PUEDE PAGAR: La caja está cerrada. Abra caja para realizar operaciones."));

        // 1. Obtener la cuota
        Cuota cuota = cuotaDao.findById(request.cuotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + request.cuotaId()));

        // 2. Validar que la cuota no esté pagada
        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

        // 3. Validar orden de cuotas
        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        // =================================================================================
        // BLOQUE LÓGICA DE MORA
        // =================================================================================

        ResultadoMora res = moraService.calcularDistribucionMora(cuota, request.montoPagado());

        BigDecimal moraACobrar = res.moraACobrar();
        BigDecimal capitalAmortizado = res.capitalAmortizado();
        boolean moraPerdonada = res.moraPerdonada();

        // =================================================================================
        // ✅ CÁLCULO CORRECTO DE REDONDEO Y VUELTO
        // =================================================================================

        BigDecimal ajusteRedondeo = BigDecimal.ZERO;
        BigDecimal vuelto = BigDecimal.ZERO;

        if (request.metodoPago() == PaymentMethod.EFECTIVO) {
            // Redondear el monto a cobrar
            BigDecimal montoRedondeado = redondearEfectivo(request.montoPagado());
            ajusteRedondeo = montoRedondeado.subtract(request.montoPagado());

            // ✅ CORRECCIÓN PRINCIPAL: El vuelto se calcula sobre el monto REDONDEADO
            if (request.montoRecibido() != null && request.montoRecibido().compareTo(montoRedondeado) > 0) {
                vuelto = request.montoRecibido().subtract(montoRedondeado);
            }

            // Verificar efectivo disponible antes de dar vuelto
            if (vuelto.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal efectivoDisponible = cajaService.calcularEfectivoDisponible(cajaActual.getId());

                if (efectivoDisponible.compareTo(vuelto) < 0) {
                    BigDecimal faltante = vuelto.subtract(efectivoDisponible);
                    throw new IllegalStateException(
                            String.format("⚠️ EFECTIVO INSUFICIENTE: Se necesita dar S/ %.2f de vuelto " +
                                            "pero solo hay S/ %.2f en caja. Faltan S/ %.2f.",
                                    vuelto, efectivoDisponible, faltante)
                    );
                }
            }
        }

        // 6. Crear el registro de pago
        Pago pago = new Pago();
        pago.setInstallment(cuota);
        pago.setAmountPaid(capitalAmortizado);
        pago.setMontMora(moraACobrar);
        pago.setMoraPerdonada(moraPerdonada);
        pago.setAmountReceived(request.montoRecibido() != null ? request.montoRecibido() : request.montoPagado());
        pago.setChange(vuelto);
        pago.setRounding(ajusteRedondeo);
        pago.setPaymentMethod(request.metodoPago());
        pago.setPaymentDate(Instant.now());
        pago.setOperationTrace(request.numeroOperacion());
        pago.setObservations(request.observaciones());
        pago.setPaymentState(PaymentState.ACTIVO);

        // 7. Actualizar la cuota
        BigDecimal nuevoMontoPagado = cuota.getAmountPaid().add(capitalAmortizado);
        BigDecimal nuevoSaldoPendiente = cuota.getBalance().subtract(capitalAmortizado);

        if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
            nuevoSaldoPendiente = BigDecimal.ZERO;
        }

        cuota.setAmountPaid(nuevoMontoPagado);
        cuota.setBalance(nuevoSaldoPendiente);

        // 8. Actualizar estado de la cuota
        if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) == 0) {
            cuota.setInstallmentState(InstallmentState.PAGADO);
        } else {
            if (LocalDate.now().isAfter(cuota.getDueDate())) {
                cuota.setInstallmentState(InstallmentState.VENCIDO);
            } else {
                cuota.setInstallmentState(InstallmentState.PAGADO_PARCIAL);
            }
        }

        // 9. Guardar cambios
        cuotaDao.save(cuota);
        Pago pagoGuardado = pagoDao.save(pago);

        // =================================================================================
        // ✅ REGISTRAR MOVIMIENTOS EN CAJA
        // =================================================================================

        // Registrar el ingreso (lo que el cliente entregó físicamente)
        cajaService.registrarPago(
                cajaActual.getId(),
                request.montoRecibido() != null ? request.montoRecibido() : request.montoPagado(),
                request.metodoPago(),
                pagoGuardado.getId(),
                cuota.getNum()
        );

        // Registrar vuelto si aplica (sale de la caja)
        if (request.metodoPago() == PaymentMethod.EFECTIVO && vuelto.compareTo(BigDecimal.ZERO) > 0) {
            cajaService.registrarVuelto(cajaActual.getId(), vuelto, pagoGuardado.getId());
        }

        log.info("✅ Pago registrado. ID: {}, Cuota: {}, Monto: {}, Recibido: {}, Vuelto: {}, Redondeo: {}",
                pagoGuardado.getId(), cuota.getNum(), request.montoPagado(),
                request.montoRecibido(), vuelto, ajusteRedondeo);

        Long pagoId = pagoGuardado.getId();

        // Emitir comprobante
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        facturacionService.emitirComprobante(pagoId);
                    }
                }
        );

        // 10. VERIFICAR FIN DEL PRÉSTAMO
        boolean prestamoCancelado = cuotaDao.findPrimeraCuotaPendiente(cuota.getLoan().getId()).isEmpty();

        if (prestamoCancelado) {
            Prestamo prestamo = cuota.getLoan();
            if (prestamo.getLoanState() != LoanState.CANCELADO) {
                prestamo.setLoanState(LoanState.CANCELADO);
                prestamoDao.save(prestamo);
                log.info("🎉 ¡Préstamo ID {} cancelado totalmente!", prestamo.getId());
            }
        }

        return PagoResponse.fromEntity(pagoGuardado);
    }

    @Override
    @Transactional
    public MercadoPagoPreferenceResponse crearPreferenciaMercadoPago(Long cuotaId, BigDecimal monto) {

        cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("⛔ NO SE PUEDE PAGAR: La caja está cerrada. Abra caja para realizar operaciones."));

        Cuota cuota = cuotaDao.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + cuotaId));

        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        ResultadoMora res = moraService.calcularDistribucionMora(cuota, monto);

        BigDecimal moraACobrar = res.moraACobrar();
        BigDecimal capitalAmortizado = res.capitalAmortizado();
        boolean moraPerdonada = res.moraPerdonada();

        if (capitalAmortizado.add(moraACobrar).compareTo(monto) != 0) {
            log.warn("⚠️ Inconsistencia redondeo mora/capital vs monto total.");
        }

        List<PreferenceItemRequest> items = new ArrayList<>();

        try {
            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

            if (capitalAmortizado.compareTo(BigDecimal.ZERO) > 0) {
                items.add(PreferenceItemRequest.builder()
                        .id("C-" + cuotaId)
                        .title("Pago Cuota #" + cuota.getNum())
                        .description("Capital")
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(capitalAmortizado)
                        .build());
            }

            if (moraACobrar.compareTo(BigDecimal.ZERO) > 0) {
                items.add(PreferenceItemRequest.builder()
                        .id("M-" + cuotaId)
                        .title("Mora por Retraso")
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(moraACobrar)
                        .build());
            }

            String urlRetorno = (backUrlBase != null && !backUrlBase.isEmpty())
                    ? backUrlBase
                    : "http://127.0.0.1:5500/src/pages/principal.html";

            boolean esHttps = urlRetorno.startsWith("https://");

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(urlRetorno)
                    .failure(urlRetorno)
                    .pending(urlRetorno)
                    .build();

            var requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .externalReference("cuota_" + cuotaId + "_" + System.currentTimeMillis());

            if (esHttps) {
                requestBuilder.autoReturn("approved");
            }

            if (webhookUrl != null && webhookUrl.startsWith("https://")) {
                requestBuilder.notificationUrl(webhookUrl);
            }

            Preference preference = new PreferenceClient().create(requestBuilder.build());

            Pago pagoPendiente = new Pago();
            pagoPendiente.setInstallment(cuota);
            pagoPendiente.setAmountPaid(capitalAmortizado);
            pagoPendiente.setMontMora(moraACobrar);
            pagoPendiente.setMoraPerdonada(moraPerdonada);
            pagoPendiente.setAmountReceived(BigDecimal.ZERO);
            pagoPendiente.setChange(BigDecimal.ZERO);
            pagoPendiente.setRounding(BigDecimal.ZERO);
            pagoPendiente.setPaymentMethod(PaymentMethod.MERCADO_PAGO);
            pagoPendiente.setPaymentState(PaymentState.PENDIENTE);
            pagoPendiente.setMercadoPagoPreferenceId(preference.getId());
            pagoPendiente.setPaymentDate(Instant.now());

            Pago pagoGuardado = pagoDao.save(pagoPendiente);

            log.info("Preferencia MP creada. ID: {}", preference.getId());

            return new MercadoPagoPreferenceResponse(
                    preference.getId(),
                    preference.getInitPoint(),
                    preference.getSandboxInitPoint(),
                    pagoGuardado.getId(),
                    cuotaId,
                    monto
            );

        } catch (Exception e) {
            log.error("Error MP: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar pago con MP: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PagoResponse procesarWebhookMercadoPago(String paymentId) {
        try {
            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);
            PaymentClient paymentClient = new PaymentClient();
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            log.info("Webhook MP. ID: {}, Status: {}", paymentId, payment.getStatus());

            Pago pago = pagoDao.findByMercadoPagoPaymentId(paymentId)
                    .orElseGet(() -> {
                        String externalReference = payment.getExternalReference();
                        if (externalReference == null || !externalReference.startsWith("cuota_")) {
                            throw new ResourceNotFoundException("Pago sin referencia: " + externalReference);
                        }
                        String[] parts = externalReference.split("_");
                        Long cuotaId = Long.parseLong(parts[1]);
                        List<Pago> pendientes = pagoDao.findByInstallmentIdAndPaymentState(cuotaId, PaymentState.PENDIENTE);
                        if (pendientes.isEmpty()) throw new ResourceNotFoundException("No hay pago pendiente local.");
                        return pendientes.get(pendientes.size() - 1);
                    });

            if ("approved".equals(payment.getStatus())) {
                if (pago.getPaymentState() == PaymentState.ACTIVO) {
                    return PagoResponse.fromEntity(pago);
                }

                pago.setPaymentState(PaymentState.ACTIVO);
                pago.setMercadoPagoPaymentId(paymentId);
                pago.setOperationTrace(paymentId);
                pago.setPaymentDate(Instant.now());

                Cuota cuota = pago.getInstallment();
                BigDecimal nuevoMonto = cuota.getAmountPaid().add(pago.getAmountPaid());
                BigDecimal nuevoSaldo = cuota.getBalance().subtract(pago.getAmountPaid());

                if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) nuevoSaldo = BigDecimal.ZERO;

                cuota.setAmountPaid(nuevoMonto);
                cuota.setBalance(nuevoSaldo);

                if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
                    cuota.setInstallmentState(InstallmentState.PAGADO);
                } else {
                    cuota.setInstallmentState(LocalDate.now().isAfter(cuota.getDueDate())
                            ? InstallmentState.PAGADO_PARCIAL : InstallmentState.PAGADO_PARCIAL);
                }

                cuotaDao.save(cuota);

            } else if ("rejected".equals(payment.getStatus()) || "cancelled".equals(payment.getStatus())) {
                pago.setPaymentState(PaymentState.ANULADO);
                pago.setMercadoPagoPaymentId(paymentId);
                pago.setObservations("Rechazado: " + payment.getStatusDetail());
            }

            Pago pagoGuardado = pagoDao.save(pago);
            Long pagoId = pagoGuardado.getId();

            if (pagoGuardado.getPaymentState() == PaymentState.ACTIVO) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                facturacionService.emitirComprobante(pagoId);
                            }
                        }
                );
            }
            return PagoResponse.fromEntity(pagoGuardado);

        } catch (Exception e) {
            log.error("Webhook Error: {}", e.getMessage());
            throw new RuntimeException("Error webhook: " + e.getMessage());
        }
    }

    @Override
    public List<PagoResponse> obtenerPagosPorCuota(Long cuotaId) { return List.of(); }

    @Override
    public List<PagoResponse> obtenerPagosPorPrestamo(Long prestamoId) { return List.of(); }

    @Override
    public List<PagoResponse> obtenerPagosPorCliente(String dni) { return List.of(); }

    private BigDecimal redondearEfectivo(BigDecimal monto) {
        BigDecimal centavos = monto.remainder(new BigDecimal("0.10"));
        BigDecimal base = monto.subtract(centavos);
        if (centavos.compareTo(new BigDecimal("0.05")) >= 0) {
            return base.add(new BigDecimal("0.10"));
        }
        return base;
    }
}