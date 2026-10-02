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
 * Se edita y se borra de verdad (sección 2.3c) -- probándolo en el teléfono,
 * un ingreso cargado mal se sintió como un dato a corregir, no un hecho
 * contable a enmendar. {@link Aporte} adoptó después el mismo criterio, por
 * el mismo motivo. El que sigue siendo un ledger inmutable (corrección =
 * asiento con `de`/`para` invertidos) es {@link com.gastoscompartidos.modelo.Liquidacion}:
 * ahí la DIRECCIÓN del pago es información que vale la pena conservar, y acá
 * no hay dirección que preservar.
 */
public record Ingreso(String id, BigDecimal monto, LocalDate fecha) {
}
