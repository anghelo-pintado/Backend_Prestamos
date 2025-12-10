package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record CierreCajaRequest(
        BigDecimal montoFisico // El conteo billete por billete
) {}