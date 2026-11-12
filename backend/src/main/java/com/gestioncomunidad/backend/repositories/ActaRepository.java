package com.gestioncomunidad.backend.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.gestioncomunidad.backend.entity.Acta;

public interface ActaRepository extends JpaRepository<Acta, Integer> {
    Optional<Acta> findByReunion_Id(Integer reunionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Acta a where a.reunion.id=:id")
    Optional<Acta> findParaActualizar(@Param("id") Integer reunionId);
}
