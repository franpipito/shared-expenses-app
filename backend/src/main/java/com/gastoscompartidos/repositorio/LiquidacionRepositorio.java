package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Liquidacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LiquidacionRepositorio extends MongoRepository<Liquidacion, String> {

    /**
     * Todas las liquidaciones del grupo, la mas nueva primero. Se esperan pocas
     * (un puñado por año, como mucho), así que sumarlas en memoria en el
     * servicio no pesa -- ver el mismo argumento en {@code totalesPorPersona}
     * de PozoServicio, que ya suma aportes en Java en vez de con un pipeline.
     */
    List<Liquidacion> findByGrupoIdOrderByFechaDescIdDesc(String grupoId);
}
