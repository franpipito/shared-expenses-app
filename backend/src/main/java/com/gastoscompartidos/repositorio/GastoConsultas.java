package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.dto.TotalPorPersona;
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
 * SEGUNDA REGLA, que llego con la vaquita: los agregados MENSUALES ignoran los
 * gastos que salieron de un pozo. Un viaje no ensucia el mes -- ni la deuda
 * entre ellos, ni el total hormiga, ni el humor de la nutria. Para eso esta
 * `sinPozo()`, y vale el mismo argumento que para la visibilidad: vive en el
 * filtro de cada consulta y no en el servicio, asi que no se puede olvidar.
 *
 * Lo que MEJORO al pasar a Mongo: en JPQL esa condicion estaba copiada y pegada
 * en las cuatro consultas, porque un string de JPQL no se puede componer. Aca es
 * un `Criteria` que devuelve un metodo y que todas reusan. Un solo lugar donde
 * mirar, y un solo lugar donde equivocarse.
 */
public interface GastoConsultas {

    /**
     * Gastos del mes que el usuario puede ver, con filtros opcionales.
     * NO incluye los gastos de un pozo: esos viven en la pantalla del viaje.
     */
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

    /** Cuanto gasto hormiga consumio este usuario en el periodo, sin los del pozo. */
    BigDecimal sumarHormigaDe(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);

    /**
     * Si hay algun gasto cargado en el periodo. Sirve para distinguir "el mes
     * pasado no gaste nada evitable" (que es un logro) de "el mes pasado no
     * usaba la app" (que no dice nada). La nutria no juzga sin datos.
     */
    long contarEn(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);

    /**
     * El saldo del periodo. Positivo = a este usuario le deben.
     *
     * Los gastos del pozo quedan afuera porque no generan deuda: la plata ya
     * se repartio al aportar. Lo que si mueve el saldo es aportar distinto, y
     * eso se ve en el pozo, no aca.
     */
    BigDecimal saldoDe(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta);

    /**
     * Lo mismo que {@link #saldoDe}, pero de TODA la historia: sin
     * {@code enElPeriodo}. Es la mitad "deudas" de {@code saldo = deudas -
     * pagos} (sección 2.3 de docs/proxima-sesion.md); la otra mitad son las
     * {@link com.gastoscompartidos.modelo.Liquidacion} ya registradas, que se
     * restan en el servicio.
     */
    BigDecimal saldoHistoricoDe(String grupoId, String usuarioId);

    /**
     * Cuanto gasto en total en gastos PERSONAL, sin recorte por mes: es el
     * lado de los debitos de "Mi Plata" (sección 2.3b), {@code restante =
     * ingresado - gastado}, mismo invariante que la vaquita pero para una
     * sola persona.
     *
     * Sin {@code $cond}: un PERSONAL siempre lo paga entero quien lo carga,
     * a diferencia de un COMPARTIDO. Y {@code sinPozo()} igual que los demas
     * agregados personales, por si algun dia existiera un PERSONAL con
     * pozoId -- esa plata ya la cuenta la vaquita, no "Mi Plata".
     */
    BigDecimal totalPersonalDe(String grupoId, String usuarioId);

    /** Todos los gastos de un pozo, sin recorte por mes: un viaje puede cruzarlo. */
    List<Gasto> buscarDelPozo(String pozoId, String grupoId);

    /** Cuanto se gasto del pozo. Es el lado de los debitos del invariante. */
    BigDecimal sumarDelPozo(String pozoId);

    /**
     * Cuanto gasto del pozo cada uno, agrupado por quien lo pago
     * ({@code pagadoPor}). Puramente informativo -- a diferencia de
     * {@code saldoDe}, esto NO genera deuda entre ellos: el invariante de la
     * vaquita (docs/vaquita.md) es que la plata ya se repartio al aportar, y
     * gastar del pozo no mueve esa cuenta. Es la misma idea que
     * {@code totalesPorPersona} en PozoServicio, pero del lado de los
     * debitos en vez de los creditos.
     */
    List<TotalPorPersona> gastadoPorPersonaDelPozo(String pozoId);

    /** Borrado de cuenta: los PERSONAL de esa persona. Devuelve cuantos borro. */
    long borrarPersonalesDe(String grupoId, String usuarioId);

    /**
     * Sumarse a un grupo o salir de uno: los PERSONAL de esa persona se mudan
     * con ella. A diferencia del borrado de cuenta, aca no se borra nada --
     * son suyos y siguen siendolo, solo que ahora los busca por otro grupoId.
     * Devuelve cuantos movio.
     */
    long moverPersonalesA(String grupoIdViejo, String usuarioId, String grupoIdNuevo);

    /**
     * Borrado de cuenta: reemplaza el nombre del pagador en los gastos que pago
     * esa persona. Los gastos quedan -- son el historial de la otra -- pero sin
     * su nombre. Devuelve cuantos toco.
     */
    long anonimizarPagador(String grupoId, String usuarioId, String nombre);
}
