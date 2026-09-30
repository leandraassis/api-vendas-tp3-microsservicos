package com.exemplo.authservice.controller;

import com.exemplo.authservice.dto.LoginRequest;
import com.exemplo.authservice.dto.LoginResponse;
import com.exemplo.authservice.dto.RefreshRequest;
import com.exemplo.authservice.model.Usuario;
import com.exemplo.authservice.service.JwtToken;
import com.exemplo.authservice.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService service;
    private final JwtToken jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Usuario usuario = this.service.autenticar(request);
        return ResponseEntity.ok(emitirTokens(usuario));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        Usuario usuario = this.service.renovar(request);
        return ResponseEntity.ok(emitirTokens(usuario));
    }

    private LoginResponse emitirTokens(Usuario usuario) {
        return new LoginResponse(jwtService.gerarAccessToken(usuario), jwtService.gerarRefreshToken(usuario),
                "Bearer", jwtService.getAccessExpiracaoSegundos());
    }
}
