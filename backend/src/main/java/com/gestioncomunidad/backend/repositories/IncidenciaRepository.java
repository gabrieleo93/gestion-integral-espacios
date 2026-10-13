package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository
        extends JpaRepository<Incidencia, Long> {

    List<Incidencia> findByUsuarioReporta_Id(Long usuarioId);

    List<Incidencia> findByVecinoVerificador_Id(Long usuarioId);

    List<Incidencia> findByTecnicoAsignado_Id(Long tecnicoId);

    List<Incidencia> findByEspacio_Comunidad_Id(Long comunidadId);
}