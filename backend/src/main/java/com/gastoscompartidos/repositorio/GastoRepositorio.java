package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de gastos.
 *
 * DECISION IMPORTANTE: la regla de visibilidad vive ACA, dentro del WHERE de
 * cada consulta, y no en el servicio como un filtro posterior.
 *
 * El motivo es que asi es imposible olvidarla. Si el filtro estuviera en el
 * servicio, el dia que alguien agregue una consulta nueva y no repita el
 * filtro, se filtran los gastos personales de la otra persona -- justamente los
 * regalos que ella pidio mantener en privado. Las reglas de seguridad van lo
 * mas abajo que se pueda.
 *
 * La regla: un gasto COMPARTIDO lo ve todo el grupo; uno PERSONAL solo lo ve
 * quien lo pago.
 */
public interface GastoRepositorio extends JpaRepository<Gasto, Long> {

    /**
     * Gastos del mes que el usuario tiene permitido ver, con filtros opcionales.
     *
     * `join fetch` trae la categoria y el pagador EN LA MISMA consulta. Sin eso,
     * al armar el DTO de cada gasto Hibernate saldria a buscar su categoria y su
     * pagador de a una: 1 consulta + 2 por gasto. Es el problema N+1, y el
     * fetch join es la forma directa de evitarlo.
     *
     * El patron `:param is null or campo = :param` es como se hacen filtros
     * opcionales en JPQL: si el parametro viene null, esa condicion es siempre
     * verdadera y no filtra nada.
     *
     * El rango de fechas es semiabierto ([desde, hasta)) a proposito: evita
     * tener que saber si el mes termina el 28, 30 o 31.
     */
    @Query("""
            select g from Gasto g
            join fetch g.categoria
            join fetch g.pagadoPor
            where g.grupo.id = :grupoId
              and g.fecha >= :desde
              and g.fecha < :hasta
              and (:categoriaId is null or g.categoria.id = :categoriaId)
              and (:pagadoPorId is null or g.pagadoPor.id = :pagadoPorId)
              and (g.tipo = com.gastoscompartidos.modelo.TipoGasto.COMPARTIDO
                   or g.pagadoPor.id = :usuarioId)
            order by g.fecha desc, g.id desc
            """)
    List<Gasto> buscarVisibles(@Param("grupoId") Long grupoId,
                               @Param("usuarioId") Long usuarioId,
                               @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta,
                               @Param("categoriaId") Long categoriaId,
                               @Param("pagadoPorId") Long pagadoPorId);

    /**
     * Un gasto por id, pero solo si el usuario puede verlo.
     *
     * Devuelve Optional vacio tanto si el gasto no existe como si existe pero es
     * privado de la otra persona. Quien llama no puede distinguir los dos casos,
     * y por eso el servicio responde 404 en ambos: un 403 confirmaria que el
     * gasto existe.
     */
    @Query("""
            select g from Gasto g
            join fetch g.categoria
            join fetch g.pagadoPor
            where g.id = :id
              and g.grupo.id = :grupoId
              and (g.tipo = com.gastoscompartidos.modelo.TipoGasto.COMPARTIDO
                   or g.pagadoPor.id = :usuarioId)
            """)
    Optional<Gasto> buscarVisiblePorId(@Param("id") Long id,
                                       @Param("grupoId") Long grupoId,
                                       @Param("usuarioId") Long usuarioId);
}
