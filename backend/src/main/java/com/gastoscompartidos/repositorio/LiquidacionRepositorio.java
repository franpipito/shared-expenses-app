package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Liquidacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LiquidacionRepositorio extends MongoRepository<Liquidacion, String> {

    /**
     * Todas las liquidaciones del grupo, la mas nueva primero. Se esperan pocas
     * (un puñado por año, como mucho), así que sumarlas en memoria en el
     * servicio no pesa -- ver el mismo argumento en {@code totalesPorPersona}
     * de PozoServicio, que ya suma aportes en Java en vez de con un pipeline.
     */
    List<Liquidacion> findByGrupoIdOrderByFechaDescIdDesc(String grupoId);

    /**
     * Para editar o borrar: el filtro por {@code grupoId} ademas del id es lo
     * que hace que tocar una liquidacion de otro grupo de 404 en vez de 500 o,
     * peor, dejar pasar el id de cualquiera.
     *
     * A diferencia de {@code Aporte} (donde solo quien aporto puede tocar SU
     * aporte), aca CUALQUIERA de los dos integrantes del grupo puede editar o
     * borrar CUALQUIER liquidacion del grupo, no solo las que tienen a esa
     * persona como `de`. Es consistente con como ya se registran: cualquiera
     * de los dos puede ser quien abre la app para anotar un pago, inclusive
     * quien lo recibio ({@code RegistrarLiquidacionRequest.meLoPagaron}), asi
     * que restringir la correccion a una sola persona no tendria de que
     * agarrarse -- no hay un campo "quien lo registro" distinto de `de`/`para`,
     * y esos dos son justamente lo que esta edicion NO toca.
     */
    Optional<Liquidacion> findByIdAndGrupoId(String id, String grupoId);

    /** Mismo criterio de alcance que el find: por grupo, no por persona. */
    long deleteByIdAndGrupoId(String id, String grupoId);
}
