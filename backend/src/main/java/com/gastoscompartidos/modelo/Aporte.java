package com.gastoscompartidos.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plata que una persona pone en el pozo. Vive EMBEBIDO en {@link Pozo}.
 *
 * **Un aporte es una liquidacion anticipada**, y esa es la idea que sostiene
 * todo el modelo de la vaquita: poner $400.000 antes del viaje es pagar por
 * adelantado gastos que todavia no se hicieron. Por eso sacar plata del pozo no
 * genera deuda entre los integrantes -- la plata ya se repartio al entrar.
 *
 * Es el mismo concepto que el javadoc de SaldoRespuesta viene anotando como
 * pendiente desde la sesion 3: "si algun dia quieren llevar la cuenta en serio,
 * la solucion es una entidad Liquidacion". Esta es esa entidad, acotada a un
 * viaje, que es la version barata de construirla.
 *
 * Va embebido y no en su propia coleccion por el mismo criterio que
 * ReferenciaUsuario: son pocos, estan acotados (dos personas, un punado de
 * aportes por viaje) y siempre se leen junto al pozo. Nunca se consultan solos.
 *
 * Es un record: inmutable, que es lo correcto para un asiento contable. Un
 * aporte no se edita; si estuvo mal, se compensa con otro.
 */
public record Aporte(ReferenciaUsuario usuario, BigDecimal monto, LocalDate fecha) {
}
