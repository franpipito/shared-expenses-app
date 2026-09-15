package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio de gastos.
 *
 * Hereda de dos interfaces: de MongoRepository vienen gratis save, findById,
 * delete y compania; de {@link GastoConsultas} vienen las consultas escritas a
 * mano, que Spring Data resuelve buscando la clase GastoConsultasImpl.
 *
 * Ese mecanismo de "fragmentos" es la respuesta de Spring Data a un problema
 * viejo: los repositorios generados alcanzan para el 80% de los casos, y para
 * el resto uno quiere escribir la consulta a mano sin perder lo generado.
 */
public interface GastoRepositorio extends MongoRepository<Gasto, String>, GastoConsultas {

    /**
     * El gasto que ya se cargo con esta clave de idempotencia, si existe.
     *
     * Se usa despues de que el indice unico rechace un insert duplicado: la cola
     * offline del telefono reintento un gasto que en realidad ya habia entrado,
     * y hay que devolverle el que existe en vez de un error.
     *
     * El grupoId va en la consulta y no despues: dos grupos distintos podrian
     * generar la misma clave, y el indice es unico POR grupo.
     */
    Optional<Gasto> findByGrupoIdAndClienteId(String grupoId, String clienteId);
}
