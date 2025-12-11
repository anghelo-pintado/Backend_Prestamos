package com.a.prestamos.model.dto.mora;

import java.math.BigDecimal;

public record ResultadoMora(
        BigDecimal moraCalculada,
        BigDecimal moraACobrar,
        BigDecimal capitalAmortizado,
        boolean moraPerdonada
) {}