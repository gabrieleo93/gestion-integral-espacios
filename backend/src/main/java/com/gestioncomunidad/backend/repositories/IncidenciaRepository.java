package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository
        extends JpaRepository<Incidencia, Integer> {

    List<Incidencia> findByUsuarioReporta_Id(Integer usuarioId);

    List<Incidencia> findByVecinoVerificador_Id(Integer usuarioId);

    List<Incidencia> findByTecnicoAsignado_Id(Integer tecnicoId);

    List<Incidencia> findByEspacio_Comunidad_Id(Integer comunidadId);

    List<Incidencia> findByUsuarioReporta_IdOrVecinoVerificador_IdOrderByFechaCreacionDescIdDesc(
            Integer usuarioReportaId,
            Integer vecinoVerificadorId);
}