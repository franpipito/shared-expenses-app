package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrupoRepositorio extends JpaRepository<Grupo, Long> {

    /** En el MVP hay un solo grupo, asi que "el primero" es "el grupo". */
    Optional<Grupo> findFirstByOrderByIdAsc();
}
