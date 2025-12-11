package com.a.prestamos.model.dto.sunat;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class InvoiceRequest {
    private String ublVersion; // "2.1"
    private String tipoOperacion; // "0101"
    private String tipoDoc; // "01" Factura, "03" Boleta
    private String serie;
    private String correlativo;
    private String fechaEmision; // "YYYY-MM-DD"
    private String tipoMoneda; // "PEN"
    private FormaPago formaPago;

    // Totales
    private BigDecimal mtoOperGravadas;
    private BigDecimal mtoOperExoneradas;// "10" Gravado, "30" Inafecto
    private BigDecimal mtoOperInafectas;
    private BigDecimal mtoIGV;
    private BigDecimal totalImpuestos;
    private BigDecimal valorVenta;
    private BigDecimal subTotal;
    private BigDecimal mtoImpVenta; // Total a pagar

    private Company company;
    private Client client;
    private List<Detail> details;
    private List<Legend> legends;

    @Data
    @Builder
    public static class Company {
        private String ruc;
        private String razonSocial;
        private String nombreComercial;
        private Address address;
    }

    @Data
    @Builder
    public static class Address {
        private String ubigueo;
        private String departamento;
        private String provincia;
        private String distrito;
        private String direccion;
    }

    @Data
    @Builder
    public static class Client {
        private String tipoDoc; // "1" DNI, "6" RUC
        private String numDoc;
        private String rznSocial;
    }

    @Data
    @Builder
    public static class Detail {
        private String codProducto;
        private String unidad; // "ZZ" Servicios
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal mtoValorUnitario; // Sin IGV
        private BigDecimal mtoValorVenta;    // Cantidad * Valor Unitario
        private BigDecimal mtoBaseIgv;
        private BigDecimal porcentajeIgv;    // 18
        private BigDecimal igv;
        private Integer tipAfeIgv;            // "10" Gravado, "30" Inafecto
        private BigDecimal totalImpuestos;
        private BigDecimal mtoPrecioUnitario; // Con IGV
    }

    @Data
    @Builder
    public static class Legend {
        private String code;  // "1000"
        private String value; // Monto en letras
    }

    @Data
    @Builder
    public static class FormaPago {
        private String moneda; // "PEN"
        private String tipo;   // "Contado"
    }
}
