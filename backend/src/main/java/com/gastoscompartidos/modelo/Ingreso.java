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
 * Record, inmutable: un ingreso no se edita; si se cargó mal, se compensa
 * con otro de signo contrario, igual que un {@link Aporte}.
 */
public record Ingreso(BigDecimal monto, LocalDate fecha) {
}
