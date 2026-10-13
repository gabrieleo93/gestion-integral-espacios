package com.gestioncomunidad.backend.repositories;

import com.gestioncomunidad.backend.entity.Espacio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspacioRepository
        extends JpaRepository<Espacio, Integer> {

    List<Espacio> findByComunidad_Id(Integer comunidadId);
}