package com.gestioncomunidad.backend.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Coste;

public interface CosteRepository extends JpaRepository<Coste, Integer> {
    List<Coste> findByIncidencia_IdOrderByFechaRegistroAscIdAsc(Integer incidenciaId);
    @Query("select c.incidencia.id from Coste c where c.id=:id")
    Optional<Integer> findIncidenciaId(@Param("id") Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coste c where c.id=:id")
    Optional<Coste> findByIdParaActualizar(@Param("id") Integer id);
}
