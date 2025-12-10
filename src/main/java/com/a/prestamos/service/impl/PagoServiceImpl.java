package com.a.prestamos.service.impl;

import com.a.prestamos.exception.prestamo.ResourceNotFoundException;
import com.a.prestamos.model.dao.CuotaDao;
import com.a.prestamos.model.dao.PagoDao;
import com.a.prestamos.model.dto.mercadoPago.MercadoPagoPreferenceResponse;
import com.a.prestamos.model.dto.pago.PagoRequest;
import com.a.prestamos.model.dto.pago.PagoResponse;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Pago;
import com.a.prestamos.model.entity.enums.InstallmentState;
import com.a.prestamos.model.entity.enums.PaymentMethod;
import com.a.prestamos.model.entity.enums.PaymentState;
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
    //private final IMoraService moraService;
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

    @Override
    @Transactional
    public PagoResponse registrarPago(PagoRequest request) {
        // 1. Obtener la cuota
        Cuota cuota = cuotaDao.findById(request.cuotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + request.cuotaId()));

        // 2. Validar que la cuota no esté pagada
        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

        if (request.montoPagado().compareTo(cuota.getBalance()) > 0) {
            throw new IllegalArgumentException("El monto no puede ser mayor al saldo pendiente.");
        }

        // 3. Validar que no haya cuotas anteriores pendientes
        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        // =================================================================================
        // BLOQUE LÓGICA DE MORA (PROFESOR)
        // =================================================================================

        BigDecimal moraCalculada = BigDecimal.ZERO;

        // Solo calculamos mora si la fecha actual es mayor al vencimiento
        if (LocalDate.now().isAfter(cuota.getDueDate())) {
            // Regla: 1% del SALDO PENDIENTE (no del original)
            moraCalculada = cuota.getBalance().multiply(TASA_MORA).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal deudaTotalExigible = cuota.getBalance().add(moraCalculada);

        // AHORA SÍ VALIDAMOS:
        // Permitimos pagar hasta (Capital + Mora).
        // Usamos una pequeña tolerancia de 0.05 por si hay temas de redondeo en efectivo,
        // pero estrictamente no debería ser mayor a deudaTotalExigible.
        if (request.montoPagado().compareTo(deudaTotalExigible) > 0) {
            throw new IllegalArgumentException(
                    "El monto (S/ " + request.montoPagado() + ") no puede ser mayor a la deuda total (Capital S/ " + cuota.getBalance() + " + Mora S/ " + moraCalculada + ")"
            );
        }

        // LÓGICA DE PERDÓN DE MORA (PAGO PARCIAL)
        // Si el cliente paga menos de la deudaTotalExigible, asumimos que es pago parcial -> Mora = 0
        boolean esPagoParcial = request.montoPagado().compareTo(deudaTotalExigible) < 0;

        BigDecimal moraACobrar;
        BigDecimal capitalAmortizado; // Parte del dinero que baja el saldo de la cuota

        if (esPagoParcial) {
            // CASO A: PAGO PARCIAL -> PERDONAMOS LA MORA
            moraACobrar = BigDecimal.ZERO;
            // Todo el dinero entra a capital
            capitalAmortizado = request.montoPagado();
            log.info("Mora de {} perdonada por pago parcial en cuota {}", moraCalculada, cuota.getId());
        } else {
            // CASO B: PAGO TOTAL -> COBRAMOS LA MORA
            moraACobrar = moraCalculada;
            // El dinero sobrante tras cobrar mora va a capital
            capitalAmortizado = request.montoPagado().subtract(moraACobrar);
        }

        // =================================================================================

        // 5. Calcular redondeo si es efectivo
        BigDecimal ajusteRedondeo = BigDecimal.ZERO;
        BigDecimal montoEfectivo = request.montoPagado();
        BigDecimal vuelto = BigDecimal.ZERO;

        if (request.metodoPago() == PaymentMethod.EFECTIVO) {
            BigDecimal montoRedondeado = redondearEfectivo(request.montoPagado());
            ajusteRedondeo = montoRedondeado.subtract(request.montoPagado());

            if (request.montoRecibido() != null && request.montoRecibido().compareTo(montoRedondeado) > 0) {
                vuelto = request.montoRecibido().subtract(montoRedondeado);
            }
            // En efectivo usamos el redondeado para cuadrar caja, pero el pago lógico sigue siendo request.montoPagado
        }

        // 6. Crear el registro de pago
        Pago pago = new Pago();
        pago.setInstallment(cuota);

        // OJO: amountPaid es lo que amortiza al capital en BD
        pago.setAmountPaid(capitalAmortizado);

        // Guardamos lo que se cobró de mora
        pago.setMontMora(moraACobrar);
        pago.setMoraPerdonada(esPagoParcial && moraCalculada.compareTo(BigDecimal.ZERO) > 0);

        pago.setAmountReceived(request.montoRecibido());
        pago.setChange(vuelto);
        pago.setRounding(ajusteRedondeo);
        pago.setPaymentMethod(request.metodoPago());
        pago.setPaymentDate(Instant.now());
        pago.setOperationTrace(request.numeroOperacion());
        pago.setObservations(request.observaciones());
        pago.setPaymentState(PaymentState.ACTIVO);

        // 7. Actualizar la cuota
        BigDecimal nuevoMontoPagado = cuota.getAmountPaid().add(request.montoPagado());
        BigDecimal nuevoSaldoPendiente = cuota.getBalance().subtract(request.montoPagado());

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
            // Si queda saldo, sigue pendiente o vencido
            if (LocalDate.now().isAfter(cuota.getDueDate())) {
                // Sigue vencida si queda saldo
                cuota.setInstallmentState(InstallmentState.VENCIDO); // O VENCIDO si tienes ese estado
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

        log.info("Pago registrado exitosamente...");
        return PagoResponse.fromEntity(pagoGuardado);
    }

    @Override
    @Transactional
    public MercadoPagoPreferenceResponse crearPreferenciaMercadoPago(Long cuotaId, BigDecimal monto) {
        // 1. Obtener la cuota
        Cuota cuota = cuotaDao.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada con ID: " + cuotaId));

        // 2. Validaciones
        if (cuota.getInstallmentState() == InstallmentState.PAGADO) {
            throw new IllegalStateException("La cuota ya está completamente pagada.");
        }

//        if (monto.compareTo(cuota.getBalance()) > 0) {
//            throw new IllegalArgumentException("El monto no puede ser mayor al saldo pendiente.");
//        }

        if (cuotaDao.existenCuotasAnterioresPendientes(cuota.getLoan().getId(), cuota.getNum())) {
            throw new IllegalStateException("Existen cuotas anteriores pendientes. Debe pagar en orden.");
        }

        // --- CÁLCULO DE MORA PARA MERCADO PAGO ---
        BigDecimal moraCalculada = BigDecimal.ZERO;
        if (LocalDate.now().isAfter(cuota.getDueDate())) {
            moraCalculada = cuota.getBalance().multiply(TASA_MORA).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal deudaTotalConMora = cuota.getBalance().add(moraCalculada);

        // Validar que no pague más de lo que debe
        if (monto.compareTo(deudaTotalConMora) > 0) {
            throw new IllegalArgumentException("El monto excede la deuda total.");
        }

        // Lógica de Desglose:
        // Si el montoPropuesto cubre TOTAL (Capital + Mora), creamos 2 ítems en MP.
        // Si el montoPropuesto es menor, asumimos pago parcial -> Mora perdonada -> 1 ítem.

        boolean esPagoTotal = monto.compareTo(deudaTotalConMora) == 0; // O muy cercano
        // Nota: Si el usuario pone manualmente "pago 100" y la deuda es 100 + 1, es parcial.

        List<PreferenceItemRequest> items = new ArrayList<>();

        try {
            // 3. Configurar Mercado Pago
            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

            if (esPagoTotal && moraCalculada.compareTo(BigDecimal.ZERO) > 0) {
                // ITEM 1: CAPITAL
                items.add(PreferenceItemRequest.builder()
                        .id("C-" + cuotaId)
                        .title("Pago Cuota #" + cuota.getNum())
                        .description("Capital Pendiente")
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(cuota.getBalance())
                        .build());

                // ITEM 2: MORA
                items.add(PreferenceItemRequest.builder()
                        .id("M-" + cuotaId)
                        .title("Mora por Retraso (1%)")
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(moraCalculada)
                        .build());
            } else {
                // PAGO PARCIAL (o sin mora): Todo es un solo concepto que va a capital
                items.add(PreferenceItemRequest.builder()
                        .id("C-" + cuotaId)
                        .title("Abono a Cuota #" + cuota.getNum())
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(monto) // El monto que puso el usuario
                        .build());
            }


            String urlRetorno = (backUrlBase != null && !backUrlBase.isEmpty())
                    ? backUrlBase
                    : "http://127.0.0.1:5500/src/pages/principal.html";

            // Verificar si la URL es HTTPS para usar auto_return
            boolean esHttps = urlRetorno.startsWith("https://");

            // 5. Configurar URLs de retorno
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(urlRetorno)
                    .failure(urlRetorno)
                    .pending(urlRetorno)
                    .build();

            // 6. Crear la preferencia
            var requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .externalReference("cuota_" + cuotaId + "_" + System.currentTimeMillis());

            // SOLO agregar auto_return si la URL es HTTPS
            if (esHttps) {
                requestBuilder.autoReturn("approved");
            }

            // SOLO AGREGAR EL WEBHOOK SI NO ES LOCALHOST O SI ESTÁ VACÍO
            if (webhookUrl != null && webhookUrl.startsWith("https://")) {
                requestBuilder.notificationUrl(webhookUrl);
            } else {
                log.warn("⚠️ No se configuró notificationUrl porque es nulo o no es HTTPS (localhost). El webhook no llegará.");
            }

            Preference preference = new PreferenceClient().create(requestBuilder.build());

            // 6. Crear registro de pago pendiente
            Pago pagoPendiente = new Pago();
            pagoPendiente.setInstallment(cuota);

            // Aquí guardamos provisionalmente. Cuando llegue el Webhook, confirmaremos la distribución real.
            // Para simplificar, asumimos que si MP aprueba, se respeta la lógica de ítems enviada.
            if (esPagoTotal) {
                pagoPendiente.setAmountPaid(cuota.getBalance());
                pagoPendiente.setMontMora(moraCalculada);
                pagoPendiente.setMoraPerdonada(false);
            } else {
                pagoPendiente.setAmountPaid(monto);
                pagoPendiente.setMontMora(BigDecimal.ZERO);
                pagoPendiente.setMoraPerdonada(true);
            }

            pagoPendiente.setAmountReceived(BigDecimal.ZERO);  // ✅ Agregar
            pagoPendiente.setChange(BigDecimal.ZERO);          // ✅ Agregar
            pagoPendiente.setRounding(BigDecimal.ZERO);        // ✅ Agregar
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
            // CAPTURAMOS EL ERROR QUE VIENE DE MERCADO PAGO
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
        // Dividir entre 0.05, redondear, y multiplicar por 0.05
        return monto.divide(REDONDEO_EFECTIVO, 0, RoundingMode.HALF_UP)
                .multiply(REDONDEO_EFECTIVO);
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
