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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoServiceImpl implements IPagoService {

    private final PagoDao pagoDao;
    private final CuotaDao cuotaDao;
    private final PrestamoDao prestamoDao;
    private final IMoraService moraService;
    private final FacturacionServiceImpl facturacionService;

    @Value("${app.mercado-pago.access-token}")
    private String mercadoPagoAccessToken;

    @Value("${app.mercado-pago.webhook-url}")
    private String webhookUrl;

    @Value("${app.mercado-pago.back-url-base}")
    private String backUrlBase;

    /**
     * Redondeo para pagos en efectivo (0.05 céntimos).
     */
    private static final BigDecimal REDONDEO_EFECTIVO = new BigDecimal("0.05");
    private static final BigDecimal TASA_MORA = new BigDecimal("0.01"); // 1%

    private final CajaDao cajaDao; // <--- AGREGAR ESTO

    @Override
    @Transactional
    public PagoResponse registrarPago(PagoRequest request) {

        cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("⛔ NO SE PUEDE PAGAR: La caja está cerrada. Abra caja para realizar operaciones."));

        // 1. Obtener la cuota
        Cuota cuota = cuotaDao.findById(request.cuotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + request.cuotaId()));

        // 2. Validar que la cuota no esté pagada
        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

        // 3. Validar que no haya cuotas anteriores pendientes
        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        // =================================================================================
        // BLOQUE LÓGICA DE MORA (CENTRALIZADA EN IMoraService)
        // =================================================================================

        ResultadoMora res = moraService.calcularDistribucionMora(cuota, request.montoPagado());

        BigDecimal moraACobrar = res.moraACobrar();
        BigDecimal capitalAmortizado = res.capitalAmortizado();
        boolean moraPerdonada = res.moraPerdonada();

        // =================================================================================

        // 5. Calcular redondeo si es efectivo
        BigDecimal ajusteRedondeo = BigDecimal.ZERO;
        BigDecimal vuelto = BigDecimal.ZERO;

        if (request.metodoPago() == PaymentMethod.EFECTIVO) {
            BigDecimal montoRedondeado = redondearEfectivo(request.montoPagado());
            ajusteRedondeo = montoRedondeado.subtract(request.montoPagado());

            System.out.printf("Redondeo efectivo: Monto original=%.2f, Monto redondeado=%.2f, Ajuste=%.2f%n",
                    request.montoPagado(), montoRedondeado, ajusteRedondeo);

            if (request.montoRecibido() != null && request.montoRecibido().compareTo(montoRedondeado) > 0) {
                vuelto = request.montoRecibido().subtract(montoRedondeado);
            }
            // En efectivo usamos el redondeado para cuadrar caja, pero el pago lógico sigue siendo request.montoPagado
        }

        // 6. Crear el registro de pago
        Pago pago = new Pago();
        pago.setInstallment(cuota);

        // Solo capitalAmortizado va contra el capital de la cuota
        pago.setAmountPaid(capitalAmortizado);
        pago.setMontMora(moraACobrar);
        pago.setMoraPerdonada(moraPerdonada);

        pago.setAmountReceived(request.montoRecibido());
        pago.setChange(vuelto);
        pago.setRounding(ajusteRedondeo);
        pago.setPaymentMethod(request.metodoPago());
        pago.setPaymentDate(Instant.now());
        pago.setOperationTrace(request.numeroOperacion());
        pago.setObservations(request.observaciones());
        pago.setPaymentState(PaymentState.ACTIVO);

        // 7. Actualizar la cuota USANDO SOLO CAPITAL AMORTIZADO
        BigDecimal nuevoMontoPagado = cuota.getAmountPaid().add(capitalAmortizado);
        BigDecimal nuevoSaldoPendiente = cuota.getBalance().subtract(capitalAmortizado);

        // Evitar saldo negativo
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

        log.info("Pago registrado exitosamente. ID: {}, Cuota: {}, Monto: {}",
                pagoGuardado.getId(), cuota.getNum(), request.montoPagado());

        Long pagoId = pagoGuardado.getId();

        // Programar la emisión del comprobante DESPUÉS del commit
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("✅ Post-commit: emitiendo comprobante para pago {}", pagoId);
                        facturacionService.emitirComprobante(pagoId); // @Async + REQUIRES_NEW
                    }
                }
        );

        // 10. VERIFICAR FIN DEL PRÉSTAMO
        boolean prestamoCancelado = cuotaDao.findPrimeraCuotaPendiente(cuota.getLoan().getId())
                .isEmpty();

        if (prestamoCancelado) {
            Prestamo prestamo = cuota.getLoan();

            if (prestamo.getLoanState() != LoanState.CANCELADO) {
                prestamo.setLoanState(LoanState.CANCELADO);
                prestamoDao.save(prestamo);
                log.info("🎉 ¡Préstamo ID {} cancelado totalmente!", prestamo.getId());
            }
        }

        log.info("Pago registrado exitosamente...");
        return PagoResponse.fromEntity(pagoGuardado);
    }

    @Override
    @Transactional
    public MercadoPagoPreferenceResponse crearPreferenciaMercadoPago(Long cuotaId, BigDecimal monto) {

        cajaDao.findByEstado(CajaState.ABIERTA)
                .orElseThrow(() -> new IllegalStateException("⛔ NO SE PUEDE PAGAR: La caja está cerrada. Abra caja para realizar operaciones."));

        // 1. Obtener la cuota
        Cuota cuota = cuotaDao.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + cuotaId));

        // 2. Validaciones
        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        // 3. Calcular distribución de mora y capital según la REGLA DE NEGOCIO CENTRALIZADA
        ResultadoMora res = moraService.calcularDistribucionMora(cuota, monto);

        BigDecimal moraCalculada = res.moraCalculada();      // Mora total calculada para esa cuota
        BigDecimal moraACobrar = res.moraACobrar();          // Parte de la mora que efectivamente se cobra con este pago
        BigDecimal capitalAmortizado = res.capitalAmortizado(); // Parte que va a capital
        boolean moraPerdonada = res.moraPerdonada();

        // (Opcional) Sanity check: capital + moraCobrar debe ser igual al monto
        BigDecimal totalDistribuido = capitalAmortizado.add(moraACobrar);
        if (totalDistribuido.compareTo(monto) != 0) {
            log.warn("⚠️ Inconsistencia en ResultadoMora: capital({}) + moraACobrar({}) != monto({})",
                    capitalAmortizado, moraACobrar, monto);
            // Podrías lanzar excepción si quieres ser más estricto:
            // throw new IllegalStateException("Inconsistencia en distribución de mora/capital");
        }

        List<PreferenceItemRequest> items = new ArrayList<>();

        try {
            // 4. Configurar Mercado Pago
            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

            // 5. Armar ítems según la distribución real
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

            // 6. Configurar URLs de retorno
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(urlRetorno)
                    .failure(urlRetorno)
                    .pending(urlRetorno)
                    .build();

            // 7. Crear la preferencia
            var requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .externalReference("cuota_" + cuotaId + "_" + System.currentTimeMillis());

            if (esHttps) {
                requestBuilder.autoReturn("approved");
            }

            if (webhookUrl != null && webhookUrl.startsWith("https://")) {
                requestBuilder.notificationUrl(webhookUrl);
            } else {
                log.warn("⚠️ No se configuró notificationUrl porque es nulo o no es HTTPS (localhost). El webhook no llegará.");
            }

            Preference preference = new PreferenceClient().create(requestBuilder.build());

            // 8. Crear registro de pago pendiente usando la MISMA distribución
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

            log.info("Preferencia Mercado Pago creada. ID: {}, Cuota: {}", preference.getId(), cuotaId);

            return new MercadoPagoPreferenceResponse(
                    preference.getId(),
                    preference.getInitPoint(),
                    preference.getSandboxInitPoint(),
                    pagoGuardado.getId(),
                    cuotaId,
                    monto
            );

        } catch (com.mercadopago.exceptions.MPApiException e) {
            String errorResponse = e.getApiResponse().getContent();
            log.error("❌ ERROR MERCADO PAGO: {}", errorResponse);
            throw new RuntimeException("MP rechazó la solicitud: " + errorResponse);

        } catch (Exception e) {
            log.error("Error al crear preferencia de Mercado Pago: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar pago con Mercado Pago: " + e.getMessage());
        }
    }


    @Override
    @Transactional
    public PagoResponse procesarWebhookMercadoPago(String paymentId) {
        try {
            // 1. Obtener información del pago desde Mercado Pago
            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);
            PaymentClient paymentClient = new PaymentClient();
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            log.info("Webhook MP recibido. Payment ID: {}, Status: {}", paymentId, payment.getStatus());

            // 2. Buscar el pago en NUESTRA base de datos
            // Intentamos buscar primero por el ID de pago de MP (si ya llegó un webhook antes)
            Pago pago = pagoDao.findByMercadoPagoPaymentId(paymentId)
                    .orElseGet(() -> {
                        // Si no existe con ese ID, buscamos por la referencia externa (la cuota)
                        String externalReference = payment.getExternalReference();
                        if (externalReference == null || !externalReference.startsWith("cuota_")) {
                            // Si no tiene referencia nuestra, es un pago huérfano o error
                            throw new ResourceNotFoundException("Pago sin referencia válida: " + externalReference);
                        }

                        // Parseamos "cuota_{id}_{timestamp}"
                        String[] parts = externalReference.split("_");
                        Long cuotaId = Long.parseLong(parts[1]);

                        // Buscamos el pago PENDIENTE más reciente para esa cuota
                        // (Asumimos que es el que el usuario acaba de intentar pagar)
                        List<Pago> pagosPendientes = pagoDao.findByInstallmentIdAndPaymentState(cuotaId, PaymentState.PENDIENTE);
                        if (pagosPendientes.isEmpty()) {
                            throw new ResourceNotFoundException("No hay orden de pago pendiente local para la cuota: " + cuotaId);
                        }
                        // Tomamos el último generado (el más reciente)
                        return pagosPendientes.get(pagosPendientes.size() - 1);
                    });

            // 3. Verificar estado y Actualizar
            String status = payment.getStatus();

            if ("approved".equals(status)) {
                // EVITAR PROCESAR DOBLE: Si ya estaba activo, no hacemos nada
                if (pago.getPaymentState() == PaymentState.ACTIVO) {
                    log.info("El pago {} ya fue procesado anteriormente.", paymentId);
                    return PagoResponse.fromEntity(pago);
                }

                // APROBADO: Confirmamos el pago
                pago.setPaymentState(PaymentState.ACTIVO);
                pago.setMercadoPagoPaymentId(paymentId);
                pago.setOperationTrace(paymentId); // Usamos el ID de MP como traza
                pago.setPaymentDate(Instant.now());

                // --- ACTUALIZACIÓN DE LA CUOTA ---
                Cuota cuota = pago.getInstallment();

                // NOTA IMPORTANTE:
                // Aquí NO calculamos mora de nuevo. Confiamos en 'pago.getAmountPaid()'.
                // En 'crearPreferencia' ya decidimos cuánto de ese dinero era Capital y cuánto era Mora.
                // pago.getAmountPaid() contiene SOLO la parte que amortiza capital.

                BigDecimal nuevoMontoPagado = cuota.getAmountPaid().add(pago.getAmountPaid());
                BigDecimal nuevoSaldoPendiente = cuota.getBalance().subtract(pago.getAmountPaid());

                // Protección contra decimales negativos
                if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
                    nuevoSaldoPendiente = BigDecimal.ZERO;
                }

                cuota.setAmountPaid(nuevoMontoPagado);
                cuota.setBalance(nuevoSaldoPendiente);

                // Definir estado de la cuota
                if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) == 0) {
                    cuota.setInstallmentState(InstallmentState.PAGADO);
                } else {
                    // Si sobró saldo, queda como parcial (o vencido si la fecha ya pasó)
                    if (LocalDate.now().isAfter(cuota.getDueDate())) {
                        // Opcional: Podrías dejarlo en PAGADO_PARCIAL o regresarlo a PENDIENTE/VENCIDO
                        // según tu lógica de negocio. Lo más seguro es PAGADO_PARCIAL.
                        cuota.setInstallmentState(InstallmentState.PAGADO_PARCIAL);
                    } else {
                        cuota.setInstallmentState(InstallmentState.PAGADO_PARCIAL);
                    }
                }

                cuotaDao.save(cuota);
                log.info("Pago MP aprobado. Cuota actualizada. ID Pago: {}", pago.getId());

            } else if ("rejected".equals(status) || "cancelled".equals(status)) {
                // RECHAZADO
                pago.setPaymentState(PaymentState.ANULADO);
                pago.setMercadoPagoPaymentId(paymentId);
                pago.setObservations("Rechazado por MP: " + payment.getStatusDetail());
                log.warn("Pago MP fallido: {}", status);

            } else {
                // PENDIENTE (in_process, pending)
                pago.setMercadoPagoPaymentId(paymentId);
                // No actualizamos saldo de cuota todavía
                log.info("Pago MP en estado intermedio: {}", status);
            }

            Pago pagoGuardado = pagoDao.save(pago);
            Long pagoId = pagoGuardado.getId();

            // 4. Emitir Comprobante (Solo si se aprobó)
            if (pagoGuardado.getPaymentState() == PaymentState.ACTIVO) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                log.info("✅ Post-commit (Webhook): emitiendo comprobante para pago {}", pagoId);
                                facturacionService.emitirComprobante(pagoId);
                            }
                        }
                );
            }

            return PagoResponse.fromEntity(pagoGuardado);


        } catch (Exception e) {
            log.error("Error procesando webhook de Mercado Pago: {}", e.getMessage(), e);
            throw new RuntimeException("Error procesando webhook: " + e.getMessage());
        }
    }

    @Override
    public List<PagoResponse> obtenerPagosPorCuota(Long cuotaId) {
        return List.of();
    }

    @Override
    public List<PagoResponse> obtenerPagosPorPrestamo(Long prestamoId) {
        return List.of();
    }

    @Override
    public List<PagoResponse> obtenerPagosPorCliente(String dni) {
        return List.of();
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Redondea un monto al múltiplo más cercano de 0.05 (para pagos en efectivo).
     */
    private BigDecimal redondearEfectivo(BigDecimal monto) {
        BigDecimal centavos = monto.remainder(new BigDecimal("0.10")); // Obtiene 0.0X
        BigDecimal base = monto.subtract(centavos); // Parte sin el último dígito de céntimos

        if (centavos.compareTo(new BigDecimal("0.05")) >= 0) {
            // 0.05, 0.06, 0.07, 0.08, 0.09 → sube a 0.10
            return base.add(new BigDecimal("0.10"));
        } else {
            // 0.00, 0.01, 0.02, 0.03, 0.04 → queda en 0.00
            return base;
        }
    }

    /**
     * Convierte una cuota a DTO con información de mora.
     */
//    private CuotaConMoraDto convertirCuotaConMora(Cuota cuota) {
//        boolean tieneMora = moraService.tieneMora(cuota);
//        BigDecimal montoMora = tieneMora ? moraService.calcularMora(cuota) : BigDecimal.ZERO;
//        long diasAtraso = moraService.calcularDiasAtraso(cuota);
//
//        return CuotaConMoraDto.fromEntity(cuota, tieneMora, montoMora, diasAtraso);
//    }
}
