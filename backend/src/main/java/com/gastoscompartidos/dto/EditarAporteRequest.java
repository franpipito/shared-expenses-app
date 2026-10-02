package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corregir el monto de un aporte ya cargado en la vaquita.
 *
 * Mismo molde que {@link EditarIngresoRequest}: se esta reemplazando el valor
 * real de ESE aporte puntual, que siempre es positivo -- no hace falta admitir
 * negativo, que era el mecanismo (ahora retirado) para compensar un aporte mal
 * cargado con un asiento en contrario.
 */
public record EditarAporteRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
