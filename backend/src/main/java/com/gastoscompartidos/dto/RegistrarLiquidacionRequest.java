package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * "Ya te pagué $X", o "ya me pagaste $X". Fijate lo que NO se pide: los ids de
 * quién paga y quién recibe -- siempre son el usuario del token y el otro
 * integrante del grupo, nunca alguien elegido a mano. Lo único que hace falta
 * decir es la DIRECCIÓN, y es lo que hace {@link #meLoPagaron}.
 *
 * Es distinto de {@link AporteRequest}, donde solo existe una dirección
 * ("puse esta plata", y nadie puede ponerla por otro): una liquidación es un
 * hecho entre dos personas, y cualquiera de las dos puede ser quien abre la
 * app para anotarlo -- inclusive quien RECIBIÓ el pago, si es ella la que
 * está mirando el teléfono en ese momento.
 *
 * @param meLoPagaron false (default): "yo pagué", de = quien manda la
 *                    request. true: "me pagaron", de = la otra persona.
 */
public record RegistrarLiquidacionRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto,
        boolean meLoPagaron
) {
}
