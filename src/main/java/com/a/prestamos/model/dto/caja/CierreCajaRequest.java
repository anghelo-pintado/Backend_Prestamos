package com.a.prestamos.model.dto.caja;

import java.math.BigDecimal;

public record CierreCajaRequest(
        BigDecimal montoFisico,
        boolean confirmarDescuadre, // <--- Nuevo campo (Check en el front: "Aceptar diferencia")
        String observaciones
) {}