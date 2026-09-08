package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;
import org.springframework.data.mongodb.repository.MongoRepository;

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
}
