package com.a.prestamos.exception.prestamo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT) // 409 Conflict
public class LoanCreationException extends RuntimeException {
    public LoanCreationException(String message) {
        super(message);
    }
}
