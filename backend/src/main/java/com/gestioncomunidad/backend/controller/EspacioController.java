package com.gestioncomunidad.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestioncomunidad.backend.dto.EspacioResponse;
import com.gestioncomunidad.backend.service.EspacioService;

@RestController
@RequestMapping ("/api/espacios")
public class EspacioController {

    private final EspacioService espacioService;

    public EspacioController(EspacioService espacioService) {
        this.espacioService = espacioService;
    }

    @GetMapping
    public List<EspacioResponse> listarDisponibles(Authentication authentication) {
        return espacioService.listarDisponibles(authentication.getName());
    }

}
