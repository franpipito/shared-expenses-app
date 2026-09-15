package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Pozo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio de pozos.
 *
 * De MongoRepository vienen gratis save y findById; de {@link PozoConsultas},
 * las dos escrituras condicionales que no se pueden expresar con save().
 *
 * Los metodos de abajo son "query methods": Spring Data los implementa
 * derivandolos del NOMBRE. `findByGrupoIdAndEstado` se traduce sola a un filtro
 * por esos dos campos. Es comodo mientras el nombre se pueda leer; cuando la
 * consulta se complica, el nombre se vuelve impronunciable y conviene bajar al
 * fragmento escrito a mano.
 */
public interface PozoRepositorio extends MongoRepository<Pozo, String>, PozoConsultas {

    /** El pozo activo del grupo. Hay a lo sumo uno: lo garantiza el indice parcial. */
    Optional<Pozo> findByGrupoIdAndEstado(String grupoId, EstadoPozo estado);

    /**
     * Un pozo por id, pero solo si es de este grupo.
     *
     * El grupoId en la consulta y no en un if posterior: si el pozo es de otro
     * grupo, esto devuelve vacio y el servicio responde 404. Un 403 confirmaria
     * que el pozo existe.
     */
    Optional<Pozo> findByIdAndGrupoId(String id, String grupoId);
}
