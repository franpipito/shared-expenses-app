package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Las consultas de gastos que no se pueden expresar con el nombre de un metodo.
 *
 * Es un "fragmento" de repositorio: Spring Data permite que una interfaz de
 * repositorio herede de varias, y para las que uno mismo implementa busca una
 * clase con el mismo nombre + "Impl". Asi `GastoRepositorio` sigue teniendo
 * gratis save, findById y delete, y ademas estos metodos escritos a mano.
 *
 * DECISION IMPORTANTE, y es la misma que con Postgres: la regla de visibilidad
 * vive ACA, adentro del filtro de cada consulta, y no en el servicio.
 *
 * Si el filtro estuviera en el servicio, el dia que alguien agregue una consulta
 * nueva y no repita el filtro, se filtran los gastos personales de la otra
 * persona -- justamente los regalos que ella pidio mantener en privado. Las
 * reglas de seguridad van lo mas abajo que se pueda.
 *
 * La regla: un gasto COMPARTIDO lo ve todo el grupo; uno PERSONAL solo lo ve
 * quien lo pago.
 *
 * Lo que MEJORO al pasar a Mongo: en JPQL esa condicion estaba copiada y pegada
 * en las cuatro consultas, porque un string de JPQL no se puede componer. Aca es
 * un `Criteria` que devuelve un metodo y que todas reusan. Un solo lugar donde
 * mirar, y un solo lugar donde equivocarse.
 */
public interface GastoConsultas {

    /** Gastos del mes que el usuario puede ver, con filtros opcionales. */
    List<Gasto> buscarVisibles(String grupoId, String usuarioId,
                               LocalDate desde, LocalDate hasta,
                               String categoriaId, String pagadoPorId);

    /**
     * Un gasto por id, pero solo si el usuario puede verlo.
     *
     * Devuelve Optional vacio tanto si el gasto no existe como si existe pero es
     * privado de la otra persona. Quien llama no puede distinguir los dos casos,
     * y por eso el servicio responde 404 en ambos: un 403 confirmaria que el
     * gasto existe.
     */
    Optional<Gasto> buscarVisiblePorId(String id, String grupoId, String usuarioId);

    /** Cuanto gasto hormiga consumio este usuario en el periodo. */
    BigDecimal sumarHormigaDe(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);

    /**
     * Si hay algun gasto cargado en el periodo. Sirve para distinguir "el mes
     * pasado no gaste nada evitable" (que es un logro) de "el mes pasado no
     * usaba la app" (que no dice nada). La nutria no juzga sin datos.
     */
    long contarEn(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);

    /** El saldo del periodo. Positivo = a este usuario le deben. */
    BigDecimal saldoDe(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);
}
