package com.gestioncomunidad.backend.config;

import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.entity.Rol;
import com.gestioncomunidad.backend.entity.Usuario;
import com.gestioncomunidad.backend.repositories.RolRepository;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DatosDemoConfig implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final String passwordDemo;

    public DatosDemoConfig(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            @Value("${DEMO_PASSWORD}") String passwordDemo) {

        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordDemo = passwordDemo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String email = "vecino.demo@example.test";

        if (usuarioRepository.existsByEmail(email)) {
            return;
        }

        if (passwordDemo.isBlank()) {
            throw new IllegalStateException(
                "DEMO_PASSWORD no puede estar vacía"
            );
        }

        Rol rol = rolRepository.findByNombre("USUARIO")
            .orElseThrow(() ->
                new IllegalStateException("Falta el rol USUARIO")
            );

        Usuario usuario = new Usuario();
        usuario.setNombre("Vecino");
        usuario.setApellidos("Demostración");
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(passwordDemo));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setRol(rol);

        usuarioRepository.save(usuario);
    }
}