package com.a.prestamos.service;

import com.a.prestamos.model.dto.authentication.AuthenticationRequest;
import com.a.prestamos.model.dto.authentication.AuthenticationResponse;
import com.a.prestamos.model.dto.authentication.UpdateRequest;

public interface IAuthenticationService {

    public AuthenticationResponse register(UpdateRequest registerRequest);
    public AuthenticationResponse authenticate(AuthenticationRequest request);
}
