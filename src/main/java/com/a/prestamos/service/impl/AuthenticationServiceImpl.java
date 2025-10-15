package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.UsuarioDao;
import com.a.prestamos.model.dto.authentication.AuthenticationRequest;
import com.a.prestamos.model.dto.authentication.AuthenticationResponse;
import com.a.prestamos.model.dto.authentication.UpdateRequest;
import com.a.prestamos.model.entity.Usuario;
import com.a.prestamos.model.entity.enums.Role;
import com.a.prestamos.service.IAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService {

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthenticationResponse register(UpdateRequest registerRequest) {

        Usuario user = usuarioDao.findByEmail(registerRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate current password
        if (!passwordEncoder.matches(registerRequest.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Update and persist new password
        user.setPassword(passwordEncoder.encode(registerRequest.getNewPassword()));
        usuarioDao.save(user);

        return getAuthenticationResponse(user);
    }

    private AuthenticationResponse getAuthenticationResponse(Usuario user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("name", user.getFirstname() + " " + user.getLastname());
        claims.put("phone", user.getPhone());

        String accessToken = jwtService.generateToken(user, claims);
        String refreshToken = jwtService.generateRefreshToken(user, claims);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        Usuario user = usuarioDao.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return getAuthenticationResponse(user);
    }
}
