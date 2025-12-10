package com.a.prestamos.model.entity.enums;

public enum PaymentState {
    ACTIVO,     // Pago válido y vigente
    ANULADO,    // Pago revertido/anulado
    PENDIENTE   // Pago iniciado pero no confirmado (para pasarela)
}
