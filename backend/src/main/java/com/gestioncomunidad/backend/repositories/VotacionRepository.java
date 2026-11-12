package com.gestioncomunidad.backend.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Votacion;

public interface VotacionRepository extends JpaRepository<Votacion, Integer> {
    Optional<Votacion> findByPresupuesto_Id(Integer presupuestoId);
    @Query("select v.presupuesto.incidencia.id from Votacion v where v.id=:id")
    Optional<Integer> findIncidenciaId(@Param("id") Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Votacion v where v.id=:id")
    Optional<Votacion> findByIdParaActualizar(@Param("id") Integer id);
}
