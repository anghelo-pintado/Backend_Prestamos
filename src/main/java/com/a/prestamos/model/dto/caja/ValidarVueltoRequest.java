package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record ValidarVueltoRequest(
        Long cuotaId,
        BigDecimal montoPagar,
        BigDecimal montoRecibido
) {}