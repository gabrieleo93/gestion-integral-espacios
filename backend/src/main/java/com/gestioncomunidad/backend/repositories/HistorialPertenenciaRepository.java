package com.gestioncomunidad.backend.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.gestioncomunidad.backend.entity.HistorialPertenencia;

public interface HistorialPertenenciaRepository extends JpaRepository<HistorialPertenencia, Long> {
    List<HistorialPertenencia> findByPertenencia_IdOrderByFechaEventoAscIdAsc(Integer pertenenciaId);
}
