package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenciaRepository
        extends JpaRepository<Incidencia, Integer> {

    // Serializa las ediciones de un mismo caso para que cada evento de historial
    // refleje los valores anteriores reales dentro de la transacción.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incidencia i where i.id = :id")
    Optional<Incidencia> findByIdParaActualizar(@Param("id") Integer id);

    // Filtramos por comunidades en SQL para incluir avisos de otros vecinos
    // sin cargar incidencias ajenas. Conservamos el orden reciente y el desempate por ID.
    List<Incidencia> findByEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(
            Collection<Integer> comunidadesIds);

    List<Incidencia> findByUsuarioReporta_Id(Integer usuarioId);

    List<Incidencia> findByVecinoVerificador_Id(Integer usuarioId);

    List<Incidencia> findByTecnicoAsignado_Id(Integer tecnicoId);

    List<Incidencia> findByEspacio_Comunidad_Id(Integer comunidadId);

    List<Incidencia> findByUsuarioReporta_IdOrVecinoVerificador_IdOrderByFechaCreacionDescIdDesc(
            Integer usuarioReportaId,
            Integer vecinoVerificadorId);
}
