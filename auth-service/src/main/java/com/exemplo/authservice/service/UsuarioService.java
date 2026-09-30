package com.exemplo.authservice.service;

import com.exemplo.authservice.dto.LoginRequest;
import com.exemplo.authservice.dto.RefreshRequest;
import com.exemplo.authservice.dto.UsuarioRequest;
import com.exemplo.authservice.model.Usuario;
import com.exemplo.authservice.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtToken jwtToken;

    public Usuario cadastrar(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ja existe um usuario cadastrado com o email " + request.getEmail());
        }

        Usuario usuario = new Usuario(
                request.getNome(),
                request.getEmail(),
                passwordEncoder.encode(request.getSenha()));

        return usuarioRepository.save(usuario);
    }

    public Usuario autenticar(LoginRequest request){
        Usuario usuario = this.usuarioRepository.findByEmail(request.getEmail())
            .orElseThrow(()-> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"E-mail ou senha inválidos"));
    
            if(!passwordEncoder.matches(request.getSenha(),usuario.getSenha())){
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"E-mail ou senha inválidos");
            }

            return usuario;

        }

    public Usuario renovar(RefreshRequest request){
        String email;
        try {
            email = jwtToken.emailDoRefreshToken(request.getRefreshToken());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou expirado");
        }

        return this.usuarioRepository.findByEmail(email)
            .orElseThrow(()-> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou expirado"));
    }
}
