package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Grupo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GrupoRepositorio extends MongoRepository<Grupo, String> {

    /**
     * En el MVP hay un solo grupo, asi que "el primero" es "el grupo".
     *
     * Con Postgres esto era findFirstByOrderByIdAsc y el orden por id era orden
     * de creacion, porque el id era un autoincremento. **Con Mongo eso sigue
     * siendo cierto, pero por otro motivo**: los primeros 4 bytes de un ObjectId
     * son el timestamp de creacion en segundos, asi que ordenar por _id ordena
     * cronologicamente. Es una propiedad del formato, no una garantia de la
     * base: dos documentos creados en el mismo segundo pueden salir en
     * cualquier orden entre si.
     */
    Optional<Grupo> findFirstByOrderByIdAsc();
}
