package com.a.prestamos.exception.prestamo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ✅ 1. Atrapamos el error de Caja (Descuadre) o Pagos (Reglas de negocio)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        Map<String, String> response = new HashMap<>();
        // Pasamos el mensaje exacto "⚠️ DESCUADRE..." al frontend
        response.put("message", e.getMessage());

        // Usamos 409 Conflict (Estado correcto para reglas de negocio)
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // ✅ 2. Atrapamos errores de argumentos (Monto negativo, etc)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, String> response = new HashMap<>();
        response.put("message", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
