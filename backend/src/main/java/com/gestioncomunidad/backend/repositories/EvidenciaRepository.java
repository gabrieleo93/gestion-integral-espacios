package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.Evidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenciaRepository
        extends JpaRepository<Evidencia, Integer> {

    List<Evidencia> findByIncidencia_IdOrderByFechaSubidaAscIdAsc(
        Integer incidenciaId
    );

    List<Evidencia> findByHistorial_Id(Long historialId);
}
