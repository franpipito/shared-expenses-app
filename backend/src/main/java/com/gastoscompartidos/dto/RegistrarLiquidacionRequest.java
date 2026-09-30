package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * "Ya te pagué $X". Fijate lo que NO se pide: a quién. Sale del token (quien
 * lo manda es "de") y del otro integrante del grupo (que es "para") -- igual
 * que en {@link AporteRequest}, es la afirmación de quien pagó, y nadie puede
 * registrar un pago en nombre de la otra persona.
 *
 * A diferencia del aporte, acá SÍ va {@code @Positive}: si se registró al
 * revés, la corrección es otra liquidación en el sentido contrario, no un
 * monto negativo. Ver el javadoc de {@link com.gastoscompartidos.modelo.Liquidacion}.
 */
public record RegistrarLiquidacionRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
