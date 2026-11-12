package com.gestioncomunidad.backend.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Reunion;

public interface ReunionRepository extends JpaRepository<Reunion, Integer> {
    List<Reunion> findByComunidad_IdOrderByFechaReunionDescIdDesc(Integer comunidadId);
    // Serializa la publicación: dos administradores no pueden publicar dos actas de la misma reunión.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reunion r where r.id=:id")
    Optional<Reunion> findByIdParaActualizar(@Param("id") Integer id);
}
