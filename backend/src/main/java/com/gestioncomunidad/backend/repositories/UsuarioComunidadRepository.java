package com.gestioncomunidad.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.entity.UsuarioComunidad;

public interface UsuarioComunidadRepository extends JpaRepository<UsuarioComunidad, Integer> {
    List<UsuarioComunidad> findByUsuario_IdOrderByIdDesc(Integer usuarioId);
    List<UsuarioComunidad> findByComunidad_IdInAndEstadoAndUsuario_Rol_NombreOrderByIdAsc(
            java.util.Collection<Integer> comunidades, EstadoUsuario estado, String rol);

    // Lecturas actuales con bloqueo para resolver una única vez cada solicitud pendiente.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from UsuarioComunidad p where p.usuario.id=:usuarioId and p.comunidad.id=:comunidadId")
    Optional<UsuarioComunidad> findParaActualizar(@org.springframework.data.repository.query.Param("usuarioId") Integer usuarioId,
            @org.springframework.data.repository.query.Param("comunidadId") Integer comunidadId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from UsuarioComunidad p where p.id=:id")
    Optional<UsuarioComunidad> findByIdParaActualizar(@org.springframework.data.repository.query.Param("id") Integer id);
    // Proyección escalar: obtiene el usuario a bloquear sin cargar una copia antigua de la pertenencia en JPA.
    @org.springframework.data.jpa.repository.Query("select p.usuario.id from UsuarioComunidad p where p.id=:id")
    Optional<Integer> findUsuarioId(@org.springframework.data.repository.query.Param("id") Integer id);
    // Filtra cuenta, rol y pertenencia en SQL para ofrecer solo candidatos de la comunidad.
    List<UsuarioComunidad> findByComunidad_IdAndEstadoAndUsuario_EstadoAndUsuario_Rol_NombreOrderByUsuario_NombreAscUsuario_IdAsc(
        Integer comunidadId, EstadoUsuario pertenencia, EstadoUsuario cuenta, String rol);
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
