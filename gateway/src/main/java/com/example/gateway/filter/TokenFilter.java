package com.example.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TokenFilter implements GlobalFilter, Ordered {

    private static final List<String> LIVRES = List.of(
            "/auth-service/auth/login",
            "/auth-service/auth/refresh",
            "/auth-service/usuarios");

    private final SecretKey chave;

    public TokenFilter(@Value("${jwt.secret}") String segredo) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String caminho = exchange.getRequest().getURI().getPath();

        if (LIVRES.contains(caminho)) {
            return chain.filter(exchange);
        }

        String cabecalho = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            return recusar(exchange);
        }

        String tipo;
        try {
            tipo = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(cabecalho.substring(7))
                    .getPayload()
                    .get("type", String.class);
        } catch (Exception e) {
            return recusar(exchange);
        }

        if (!"access".equals(tipo)) {
            return recusar(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> recusar(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
