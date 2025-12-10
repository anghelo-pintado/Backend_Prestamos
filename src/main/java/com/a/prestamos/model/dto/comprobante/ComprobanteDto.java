package com.a.prestamos.model.dto.comprobante;

import com.a.prestamos.model.entity.Comprobante;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComprobanteDto {

    private Long id;
    private String tipo;         // 01 o 03
    private String serie;
    private Long correlativo;
    private String pdfUrl;
    private String fecha;
    private String monto;

    public static ComprobanteDto fromEntity(Comprobante c) {
        return ComprobanteDto.builder()
                .id(c.getId())
                .tipo(c.getProofType())
                .serie(c.getSerie())
                .correlativo(c.getSequential())
                .pdfUrl(c.getUrlPdf())
                .fecha(c.getIssueDate() != null ? c.getIssueDate().toString() : null)
                .monto(c.getPayment() != null ? c.getPayment().getAmountPaid().toString() : "0.00")
                .build();
    }
}

