package com.gastoscompartidos.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plata que declara tener una persona. Vive EMBEBIDO en {@link Usuario}.
 *
 * Es el mismo concepto que {@link Aporte}, pero para una sola persona en vez
 * de dos: "Mi Plata" (sección 2.3b) es {@code restante = ingresos - gastos
 * personales}, igual que la vaquita es {@code restante = aportes - gastos}.
 * Por eso no lleva un campo `usuario` como Aporte -- ya está embebido dentro
 * del dueño, no hace falta decir de quién es.
 *
 * A DIFERENCIA de {@link Aporte} y {@link com.gastoscompartidos.modelo.Liquidacion}
 * (sección 2.3c, v1.1): este SÍ se edita y se borra. Los otros dos siguen
 * siendo ledgers inmutables (corrección = asiento contrario) porque ahí
 * importa el rastro de los dos movimientos; acá, probándolo en el teléfono,
 * un ingreso cargado mal se sintió como un dato a corregir, no un hecho
 * contable a enmendar -- de ahí el `id`, que los otros dos no necesitan.
 */
public record Ingreso(String id, BigDecimal monto, LocalDate fecha) {
}
