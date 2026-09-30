package com.exemplo.authservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.exemplo.authservice.model.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtToken {

    public static final String CLAIM_TIPO = "type";
    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessExpiracaoMs;
    private final long refreshExpiracaoMs;

    public JwtToken(@Value("${jwt.secret}") String segredo,
                    @Value("${jwt.access-expiration:900000}") long accessExpiracaoMs,
                    @Value("${jwt.refresh-expiration:604800000}") long refreshExpiracaoMs) {
        this.secretKey = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.accessExpiracaoMs = accessExpiracaoMs;
        this.refreshExpiracaoMs = refreshExpiracaoMs;
    }

    public String gerarAccessToken(Usuario usuario){
        return gerar(usuario, TIPO_ACCESS, accessExpiracaoMs);
    }

    public String gerarRefreshToken(Usuario usuario){
        return gerar(usuario, TIPO_REFRESH, refreshExpiracaoMs);
    }

    public long getAccessExpiracaoSegundos(){
        return accessExpiracaoMs / 1000;
    }

    public String emailDoRefreshToken(String token){
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!TIPO_REFRESH.equals(claims.get(CLAIM_TIPO, String.class))) {
            throw new JwtException("Token informado nao e um refresh token");
        }
        return claims.getSubject();
    }

    private String gerar(Usuario usuario, String tipo, long expiracaoMs){
        Date agora = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(usuario.getEmail())
                .claim(CLAIM_TIPO, tipo)
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoMs))
                .signWith(secretKey)
                .compact();
    }

}
