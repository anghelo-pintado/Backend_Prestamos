package com.a.prestamos.service;

import com.a.prestamos.model.dto.mora.ResultadoMora;
import com.a.prestamos.model.entity.Cuota;

import java.math.BigDecimal;

public interface IMoraService {
    ResultadoMora calcularDistribucionMora(Cuota cuota, BigDecimal montoPagado);
}
