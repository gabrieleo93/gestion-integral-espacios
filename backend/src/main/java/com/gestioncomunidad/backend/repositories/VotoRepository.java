package com.gestioncomunidad.backend.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Voto;

public interface VotoRepository extends JpaRepository<Voto, Integer> {
    List<Voto> findByVotacion_Id(Integer votacionId);
    // Lectura actual tras obtener el bloqueo de votación: evita recuentos de una instantánea anterior.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voto v where v.votacion.id=:id")
    List<Voto> findParaActualizar(@Param("id") Integer id);
}
