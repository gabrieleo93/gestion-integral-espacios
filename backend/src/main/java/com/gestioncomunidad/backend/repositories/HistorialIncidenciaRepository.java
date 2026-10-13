package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.HistorialIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialIncidenciaRepository
        extends JpaRepository<HistorialIncidencia, Long> {

    List<HistorialIncidencia> findByIncidencia_IdOrderByFechaEventoAscIdAsc(
        Integer incidenciaId
    );
}
