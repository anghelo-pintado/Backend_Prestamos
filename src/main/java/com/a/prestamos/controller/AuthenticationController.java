package com.a.prestamos.controller;

import com.a.prestamos.model.dto.authentication.AuthenticationRequest;
import com.a.prestamos.model.dto.authentication.AuthenticationResponse;
import com.a.prestamos.model.dto.authentication.UpdateRequest;
import com.a.prestamos.service.IAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final IAuthenticationService IAuthenticationService;

    @PostMapping("/actualizar")
    public ResponseEntity<AuthenticationResponse> register(@RequestBody UpdateRequest request) {
        return ResponseEntity.ok(IAuthenticationService.register(request));
    }

    @PostMapping("/autenticar")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(IAuthenticationService.authenticate(request));
    }

}

