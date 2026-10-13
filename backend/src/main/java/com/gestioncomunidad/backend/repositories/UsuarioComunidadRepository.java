package com.gestioncomunidad.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.entity.UsuarioComunidad;

public interface UsuarioComunidadRepository extends JpaRepository<UsuarioComunidad, Integer> {
     Optional<UsuarioComunidad> findByUsuarioIdAndComunidadId(Integer usuarioId, Integer comunidadId);

     List<UsuarioComunidad> findByUsuario_IdAndEstado(
        Integer usuarioId,
        EstadoUsuario estado
    );

    boolean existsByUsuario_IdAndComunidad_IdAndEstado(
        Integer usuarioId,
        Integer comunidadId,
        EstadoUsuario estado
    );

}
