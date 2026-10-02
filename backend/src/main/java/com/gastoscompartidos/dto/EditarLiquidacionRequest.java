package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corregir el monto de una liquidacion ya registrada.
 *
 * Mismo molde que {@link EditarAporteRequest}, pero fijate lo que NO tiene:
 * no hay `meLoPagaron` ni ningun campo para `de`/`para`. La direccion de una
 * liquidacion -- quien le pago a quien -- no se edita por aca; si se anoto al
 * reves, la correccion es borrarla y volver a registrarla bien. Lo que esto
 * arregla es un numero mal tipeado, no quien pago.
 */
public record EditarLiquidacionRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
