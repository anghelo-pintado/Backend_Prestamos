package com.a.prestamos.model.dto.apiclient;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SunatResponseDto(
        @JsonProperty("numero_documento") String numeroDocumento,
        @JsonProperty("razon_social") String razonSocial,
        String estado,
        String condicion,
        String direccion,
        String distrito,
        String provincia,
        String departamento,
        @JsonProperty("es_agente_retencion") Boolean esAgenteRetencion,
        @JsonProperty("es_buen_contribuyente") Boolean esBuenContribuyente
) {}
