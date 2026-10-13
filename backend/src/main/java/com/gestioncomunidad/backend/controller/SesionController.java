package com.gestioncomunidad.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sesion")
public class SesionController {

    @GetMapping
    public SesionResponse consultar(Authentication authentication) {
        List<String> roles = authentication.getAuthorities()
            .stream()
            .map(autoridad -> autoridad.getAuthority())
            .toList();

        return new SesionResponse(authentication.getName(), roles);
    }

    public record SesionResponse(
        String email,
        List<String> roles
    ) {
    }
}