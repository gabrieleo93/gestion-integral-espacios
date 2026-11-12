package com.gestioncomunidad.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestioncomunidad.backend.entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    // Serializa altas de pertenencia del mismo usuario, incluso cuando aún no existe la relación.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findByIdParaActualizar(@org.springframework.data.repository.query.Param("id") Integer id);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);


}

