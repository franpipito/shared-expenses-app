package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corregir el monto de un ingreso ya cargado en "Mi Plata" (sección 2.3c).
 *
 * A diferencia de {@link RegistrarIngresoRequest} (que admite negativo, para
 * el asiento en contrario de {@code Aporte}/{@code Liquidacion}), acá no
 * hace falta: se está reemplazando el valor real de ESE ingreso puntual, que
 * siempre es positivo -- por eso {@code Ingreso} ganó un {@code id} y estos
 * dos, a diferencia de los otros ledgers de la app, dejaron de ser
 * inmutables.
 */
public record EditarIngresoRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
