package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.ComprobanteDao;
import com.a.prestamos.model.dao.PagoDao;
import com.a.prestamos.model.dto.sunat.InvoiceRequest;
import com.a.prestamos.model.entity.Cliente;
import com.a.prestamos.model.entity.Comprobante;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Pago;
import com.a.prestamos.util.NumberToLetterConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturacionServiceImpl {

    private final ComprobanteDao comprobanteDao;
    private final PagoDao pagoDao; // <--- AGREGAR ESTO (Inyeccción)
    private final RestTemplate restTemplate = new RestTemplate();
    private final S3Client s3;

    @Value("${app.apisperu.api-url}")
    private String apiUrl;

    @Value("${app.apisperu.api-key}")
    private String apiToken;

    @Value("${app.apisperu.ruc-emisor}")
    private String emisorRuc;

    @Value("${app.apisperu.razon-social-emisor}")
    private String emisorRazonSocial;

    @Value("${app.apisperu.emisor-ubigueo}")
    private String emisorUbigueo;

    @Value("${app.apisperu.emisor-departamento}")
    private String emisorDepartamento;

    @Value("${app.apisperu.emisor-provincia}")
    private String emisorProvincia;

    @Value("${app.apisperu.emisor-distrito}")
    private String emisorDistrito;

    @Value("${app.apisperu.emisor-direccion}")
    private String emisorDireccion;

    @Value("${app.do.spaces.bucket-name}")
    private String bucket;

    @Value("${app.do.spaces.endpoint-url}")
    private String spacesEndpoint;

    // CAMBIO CLAVE: Recibe ID, no Entidad. Y crea su propia transacción.
    @Async // Opcional si usas el ejecutor de Spring, pero con tu new Thread funciona igual
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitirComprobante(Long pagoId) {
        try {

            if (comprobanteDao.existsByPaymentId(pagoId)) {
                log.info("Ya existe comprobante para el pago {}. Omitiendo.", pagoId);
                return;
            }

            Pago pago = pagoDao.findById(pagoId)
                    .orElseThrow(() -> new RuntimeException("Pago no encontrado para facturar"));

            Cuota cuota = pago.getInstallment();
            Cliente cliente = cuota.getLoan().getCustomer();

            // 1. Determinar Tipo y Serie
            boolean esRuc = cliente.getDocumentId().length() == 11;
            String tipoDoc = esRuc ? "01" : "03"; // 01 Factura, 03 Boleta
            String serie = esRuc ? "F001" : "B001";

            // Obtener correlativo (Lógica simple, idealmente usar una secuencia en BD)
            long ultimoCorrelativo = comprobanteDao.countComprobantesBySerie(serie);
            String correlativo = String.format("%08d", ultimoCorrelativo + 1);

            // 2. CALCULAR PROPORCIÓN DEL PAGO
            BigDecimal montoPagado = pago.getAmountPaid();
            BigDecimal totalCuota = cuota.getAmount(); // Monto total de la cuota

            // Proporción pagada (ej: 50/88 = 0.568)
            BigDecimal proporcion = montoPagado.divide(totalCuota, 10, RoundingMode.HALF_UP);

            // 3. Calcular montos proporcionales
            BigDecimal interesOriginal = cuota.getInterest();
            BigDecimal igvOriginal = cuota.getIgv();
            BigDecimal capitalOriginal = cuota.getPrincipal();

            // Aplicar proporción
            BigDecimal interesProporcional = interesOriginal.multiply(proporcion).setScale(2, RoundingMode.HALF_UP);
            BigDecimal igvProporcional = igvOriginal.multiply(proporcion).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capitalProporcional = capitalOriginal.multiply(proporcion).setScale(2, RoundingMode.HALF_UP);

            // Ajustar para que la suma sea exactamente el monto pagado
            BigDecimal sumaParcial = interesProporcional.add(igvProporcional).add(capitalProporcional);
            BigDecimal diferencia = montoPagado.subtract(sumaParcial);
            capitalProporcional = capitalProporcional.add(diferencia); // Ajustar en capital

            // 4. Construir Detalles
            List<InvoiceRequest.Detail> detalles = new ArrayList<>();
            BigDecimal totalGravadas = BigDecimal.ZERO;
            BigDecimal totalInafectas = BigDecimal.ZERO;
            BigDecimal totalIgv = BigDecimal.ZERO;

            // ITEM A: Intereses (Gravado con IGV) - Solo si hay interés
            if (interesProporcional.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal precioUnitario = interesProporcional.add(igvProporcional);

                detalles.add(InvoiceRequest.Detail.builder()
                        .codProducto("INT-001")
                        .unidad("ZZ")
                        .descripcion("INTERESES POR PRESTAMO CUOTA " + cuota.getNum() +
                                (proporcion.compareTo(BigDecimal.ONE) < 0 ? " (PAGO PARCIAL)" : ""))
                        .cantidad(BigDecimal.ONE)
                        .mtoValorUnitario(interesProporcional)
                        .mtoValorVenta(interesProporcional)
                        .mtoBaseIgv(interesProporcional)
                        .porcentajeIgv(new BigDecimal("18"))
                        .igv(igvProporcional)
                        .tipAfeIgv(10)
                        .totalImpuestos(igvProporcional)
                        .mtoPrecioUnitario(precioUnitario)
                        .build());

                totalGravadas = totalGravadas.add(interesProporcional);
                totalIgv = totalIgv.add(igvProporcional);
            }

            // ITEM B: Amortización Capital (Inafecto) - Solo si hay capital
            if (capitalProporcional.compareTo(BigDecimal.ZERO) > 0) {
                detalles.add(InvoiceRequest.Detail.builder()
                        .codProducto("CAP-001")
                        .unidad("ZZ")
                        .descripcion("AMORTIZACIÓN DE CAPITAL CUOTA " + cuota.getNum() +
                                (proporcion.compareTo(BigDecimal.ONE) < 0 ? " (PAGO PARCIAL)" : ""))
                        .cantidad(BigDecimal.ONE)
                        .mtoValorUnitario(capitalProporcional)
                        .mtoValorVenta(capitalProporcional)
                        .mtoBaseIgv(capitalProporcional)
                        .porcentajeIgv(BigDecimal.ZERO)
                        .igv(BigDecimal.ZERO)
                        .tipAfeIgv(30)
                        .totalImpuestos(BigDecimal.ZERO)
                        .mtoPrecioUnitario(capitalProporcional)
                        .build());

                totalInafectas = totalInafectas.add(capitalProporcional);
            }

            // 5. Totales (deben sumar exactamente el monto pagado)
            BigDecimal totalVenta = montoPagado; // Usar el monto pagado directamente

            // 6. Construir Invoice
            String fechaEmision = LocalDate.now()
                    .atStartOfDay()
                    .atZone(ZoneId.of("America/Lima"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));

            // 4. Construir Objeto Principal
            InvoiceRequest invoice = InvoiceRequest.builder()
                    .ublVersion("2.1")
                    .tipoOperacion("0101")
                    .tipoDoc(tipoDoc)
                    .serie(serie)
                    .correlativo(correlativo)
                    .fechaEmision(fechaEmision)
                    .tipoMoneda("PEN")
                    .formaPago(InvoiceRequest.FormaPago.builder()
                            .moneda("PEN")
                            .tipo("Contado")
                            .build())
                    .client(InvoiceRequest.Client.builder()
                            .tipoDoc(esRuc ? "6" : "1") // 6=RUC, 1=DNI
                            .numDoc(cliente.getDocumentId())
                            .rznSocial(cliente.getFullName()) // O Razón Social
                            .build())
                    .company(InvoiceRequest.Company.builder()
                            .ruc(emisorRuc)
                            .razonSocial(emisorRazonSocial)
                            .nombreComercial(emisorRazonSocial)
                            .address(InvoiceRequest.Address.builder()
                                    .ubigueo(emisorUbigueo)
                                    .departamento(emisorDepartamento)
                                    .provincia(emisorProvincia)
                                    .distrito(emisorDistrito)
                                    .direccion(emisorDireccion)
                                    .build())
                            .build())
                    .mtoOperGravadas(totalGravadas)
                    .mtoOperExoneradas(BigDecimal.ZERO)
                    .mtoOperInafectas(totalInafectas)
                    .mtoIGV(totalIgv)
                    .totalImpuestos(totalIgv)
                    .valorVenta(totalGravadas.add(totalInafectas))
                    .subTotal(totalVenta)
                    .mtoImpVenta(totalVenta)
                    .details(detalles)
                    .legends(List.of(InvoiceRequest.Legend.builder()
                            .code("1000")
                            .value(NumberToLetterConverter.convert(totalVenta).toUpperCase()) // Necesitas un utilitario para esto o hardcodear por ahora
                            .build()))
                    .build();

            // 5. Enviar a la API
            enviarASunat(invoice, pago, serie, correlativo, tipoDoc);

        } catch (Exception e) {
            log.error("Error emitiendo comprobante para pago " + pagoId, e);
            // No lanzamos excepción para no revertir el pago, solo logueamos el error de facturación
        }
    }

    private void enviarASunat(InvoiceRequest invoice, Pago pago, String serie, String correlativo, String tipoDoc) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        HttpEntity<InvoiceRequest> request = new HttpEntity<>(invoice, headers);

        try {
            log.info("Request a SUNAT: {}", new ObjectMapper().writeValueAsString(invoice));

            // Llamada POST a /invoice/send
            JsonNode response = restTemplate.postForObject(apiUrl + "/invoice/send", request, JsonNode.class);


            // Leer respuesta
            String xmlUrl = response.path("xml").asText();
            String hash = response.path("hash").asText();

            // Verificar respuesta SUNAT
            JsonNode sunatResponse = response.path("sunatResponse");
            boolean success = sunatResponse.path("success").asBoolean();

            if (!success) {
                String error = sunatResponse.path("error").path("message").asText();
                log.error("SUNAT rechazó el comprobante: {}", error);
                return;
            }

            // 2. Obtener PDF
            String pdfUrl = obtenerPdf(invoice, serie, correlativo);

            // 6. Guardar Comprobante en BD
            Comprobante comprobante = new Comprobante();
            comprobante.setPayment(pago);
            comprobante.setSerie(serie);
            comprobante.setSequential(Long.valueOf(correlativo));
            comprobante.setProofType(tipoDoc);
            comprobante.setUrlXml(xmlUrl);
            comprobante.setUrlPdf(pdfUrl);
            comprobante.setHash(hash);  // Útil para consultas

            comprobanteDao.save(comprobante);
            log.info("Comprobante emitido: {}-{}", serie, correlativo);

        } catch (Exception e) {
            log.error("Error conectando con APIsPERU", e);
        }
    }

    private String obtenerPdf(InvoiceRequest invoice, String serie, String correlativo) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            // 🔥 Aquí enviamos el MISMO objeto invoice que se usó en /invoice/send
            HttpEntity<InvoiceRequest> request = new HttpEntity<>(invoice, headers);

            // Endpoint /invoice/pdf → retorna PDF en byte[]
            byte[] pdfBytes = restTemplate.postForObject(apiUrl + "/invoice/pdf", request, byte[].class);

            if (pdfBytes == null || pdfBytes.length == 0)
                return null;

            String fileName = "comprobantes/" + serie + "-" + correlativo + ".pdf";

            // SUBIR A SPACE
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType("application/pdf")
                    .acl(ObjectCannedACL.PUBLIC_READ) // si quieres acceso directo
                    .build();

            s3.putObject(put, RequestBody.fromBytes(pdfBytes));

            // URL PÚBLICA
            String publicUrl = spacesEndpoint.replace("https://", "https://" + bucket + ".")
                    + "/" + fileName;

            return publicUrl;

        } catch (Exception e) {
            log.error("Error obteniendo PDF: {}", e.getMessage());
        }

        return null;
    }
}
