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
 * **Se edita y se borra de verdad** (al igual que {@link Ingreso}, y a
 * diferencia de {@link Liquidacion}, que sigue siendo un ledger inmutable).
 * Hasta ahora la correccion era un asiento en contrario -- "si estuvo mal, se
 * compensa con otro" -- pero probarlo junto con el resto de los ledgers de la
 * app dejo claro que el mismo argumento de Ingreso aplica igual de bien aca:
 * un aporte tipeado mal es un dato a corregir, no un hecho contable que valga
 * la pena enmendar con un segundo asiento. La diferencia con Liquidacion es
 * que ahi la DIRECCION del pago (quien le pago a quien) es informacion en si
 * misma que vale la pena conservar; en un aporte no hay direccion que
 * preservar, solo un monto a nombre de quien lo puso.
 *
 * Por eso gano un {@code id}: sin el no habia forma de direccionar CUAL
 * aporte tocar, mismo motivo por el que {@code Ingreso} lo gano antes.
 */
public record Aporte(String id, ReferenciaUsuario usuario, BigDecimal monto, LocalDate fecha) {
}
