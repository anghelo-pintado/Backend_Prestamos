package com.a.prestamos.model.entity.enums;

public enum PaymentMethod {
    EFECTIVO,         // Aplica redondeo
    TARJETA_CREDITO,  // Exacto
    TARJETA_DEBITO,   // Exacto
    BILLETERA_DIGITAL, // Yape/Plin (Exacto)
    MERCADO_PAGO,     // Exacto
}
