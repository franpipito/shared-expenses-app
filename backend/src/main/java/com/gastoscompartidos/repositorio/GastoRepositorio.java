package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /**
     * Cuanto gasto hormiga consumio este usuario en el periodo.
     *
     * "Su parte", no el monto total: si pago el, su parte es montoPagador; si
     * pago el otro, es lo que le debe. Es la misma cuenta que `Gasto.parteDe()`,
     * pero expresada en SQL para no traer las filas solo para sumarlas.
     *
     * `coalesce(..., 0)` porque un SUM sin filas devuelve null, no cero.
     */
    @Query("""
            select coalesce(sum(
                       case when g.pagadoPor.id = :usuarioId then g.montoPagador
                            else g.monto - g.montoPagador end), 0)
            from Gasto g
            where g.grupo.id = :grupoId
              and g.esHormiga = true
              and g.fecha >= :desde
              and g.fecha < :hasta
              and (g.tipo = com.gastoscompartidos.modelo.TipoGasto.COMPARTIDO
                   or g.pagadoPor.id = :usuarioId)
            """)
    BigDecimal sumarHormigaDe(@Param("grupoId") Long grupoId,
                              @Param("usuarioId") Long usuarioId,
                              @Param("desde") LocalDate desde,
                              @Param("hasta") LocalDate hasta);

    /**
     * Si hay algun gasto cargado en el periodo. Sirve para distinguir "el mes
     * pasado no gaste nada evitable" (que es un logro) de "el mes pasado no
     * usaba la app" (que no dice nada). La nutria no juzga sin datos.
     */
    @Query("""
            select count(g) from Gasto g
            where g.grupo.id = :grupoId
              and g.fecha >= :desde
              and g.fecha < :hasta
              and (g.tipo = com.gastoscompartidos.modelo.TipoGasto.COMPARTIDO
                   or g.pagadoPor.id = :usuarioId)
            """)
    long contarEn(@Param("grupoId") Long grupoId,
                  @Param("usuarioId") Long usuarioId,
                  @Param("desde") LocalDate desde,
                  @Param("hasta") LocalDate hasta);

    /**
     * EL SALDO, calculado al vuelo.
     *
     * Positivo = a este usuario le deben. Negativo = debe el.
     *
     * Esta es toda la implementacion: un SUM sobre el indice
     * idx_gasto_grupo_fecha. No hay tabla de saldo, no hay nada que mantener
     * sincronizado, y es imposible que quede desactualizado porque se deriva de
     * los gastos cada vez.
     *
     * Que sea tan barato es consecuencia directa de la decision de la sesion 1:
     * como `montoPagador` ya viene resuelto en la fila, no hay que dividir ni
     * redondear nada por gasto. Si hubieramos guardado un porcentaje, esto
     * tendria aritmetica por fila y el caso para materializar seria mas fuerte.
     *
     * Solo los COMPARTIDO: un gasto personal tiene montoPagador == monto, asi
     * que aportaria cero igual, pero filtrarlo deja la intencion explicita.
     */
    @Query("""
            select coalesce(sum(
                       case when g.pagadoPor.id = :usuarioId then g.monto - g.montoPagador
                            else g.montoPagador - g.monto end), 0)
            from Gasto g
            where g.grupo.id = :grupoId
              and g.tipo = com.gastoscompartidos.modelo.TipoGasto.COMPARTIDO
              and g.fecha >= :desde
              and g.fecha < :hasta
            """)
    BigDecimal saldoDe(@Param("grupoId") Long grupoId,
                       @Param("usuarioId") Long usuarioId,
                       @Param("desde") LocalDate desde,
                       @Param("hasta") LocalDate hasta);
}
