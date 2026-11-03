package com.gestioncomunidad.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestioncomunidad.backend.dto.EspacioResponse;
import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.EspacioRepository;
import com.gestioncomunidad.backend.repositories.UsuarioComunidadRepository;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;



@Service
public class EspacioService {
    private final UsuarioRepository usuarioRepository;
    private final UsuarioComunidadRepository usuarioComunidadRepository;
    private final EspacioRepository espacioRepository;

    public EspacioService(UsuarioRepository usuarioRepository, UsuarioComunidadRepository usuarioComunidadRepository, EspacioRepository espacioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioComunidadRepository = usuarioComunidadRepository;
        this.espacioRepository = espacioRepository;
    }

    @Transactional(readOnly = true)
    public List<EspacioResponse> listarDisponibles(String email){
         var usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "La cuenta de la sesión ya no está disponible"));

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new OperacionNoPermitidaException(
                    "La cuenta de usuario debe estar activa");
     }
             String rol = usuario.getRol().getNombre();

        if (!"USUARIO".equals(rol) && !"ADMINISTRADOR".equals(rol)) {
            throw new OperacionNoPermitidaException(
                    "La consulta de espacios está habilitada para vecinos y administradores");
        }

        var pertenencias = usuarioComunidadRepository
                .findByUsuario_IdAndEstado(
                        usuario.getId(),
                        EstadoUsuario.ACTIVO);

        return pertenencias.stream()
                .flatMap(pertenencia -> espacioRepository
                        .findByComunidad_Id(pertenencia.getComunidad().getId())
                        .stream())
                .map(espacio -> new EspacioResponse(
                        espacio.getId(),
                        espacio.getNombre(),
                        espacio.getTipo(),
                        espacio.getComunidad().getId(),
                        espacio.getComunidad().getNombre()))
                .sorted(Comparator
                        .comparing(EspacioResponse::comunidadNombre)
                        .thenComparing(EspacioResponse::nombre)
                        .thenComparing(EspacioResponse::id))
                .toList();
    }


}
