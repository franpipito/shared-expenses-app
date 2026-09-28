package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Grupo;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Aca habia un `findFirstByOrderByIdAsc()`: cuando habia un solo grupo en toda
 * la base, "el primero" era "el grupo". Con el registro abierto cada persona
 * tiene el suyo, y el grupo de alguien se busca siempre por el `grupoId` de
 * su usuario, nunca por posicion.
 */
public interface GrupoRepositorio extends MongoRepository<Grupo, String> {
}
