package com.gestioncomunidad.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.gestioncomunidad.backend.entity.Comunidad;


public interface ComunidadRepository  extends JpaRepository<Comunidad, Integer> {

}
