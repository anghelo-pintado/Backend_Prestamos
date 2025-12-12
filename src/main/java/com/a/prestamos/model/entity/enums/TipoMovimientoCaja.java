package com.a.prestamos.model.entity.enums;

public enum TipoMovimientoCaja {
    REINGRESO,      // Cuando se agrega dinero a la caja
    RETIRO,         // Cuando se retira dinero de la caja
    PAGO,
    PAGO_VUELTO,    // Registro automático al dar vuelto
    AJUSTE          // Ajustes manuales
}