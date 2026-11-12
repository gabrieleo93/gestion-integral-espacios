package com.gestioncomunidad.backend.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Presupuesto;

public interface PresupuestoRepository extends JpaRepository<Presupuesto, Integer> {
    List<Presupuesto> findByIncidencia_IdOrderByFechaCreacionDescIdDesc(Integer incidenciaId);
    @Query("select p.incidencia.id from Presupuesto p where p.id=:id")
    Optional<Integer> findIncidenciaId(@Param("id") Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Presupuesto p where p.id=:id")
    Optional<Presupuesto> findByIdParaActualizar(@Param("id") Integer id);
}
