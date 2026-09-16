package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * Poner plata en el pozo.
 *
 * Fijate lo que NO se pide: quien aporta. Sale del token, igual que en los
 * gastos. **Nadie puede registrar un aporte a nombre de la otra persona**, que
 * es lo correcto: un aporte es la afirmacion "puse esta plata", y eso solo lo
 * puede decir quien la puso.
 *
 * Tampoco se pide la fecha: es hoy. Un aporte es un hecho del momento en que se
 * registra.
 */
public record AporteRequest(

        /*
         * SE ADMITE NEGATIVO, y es la unica forma de deshacer un aporte.
         *
         * `docs/vaquita.md` dice que un aporte es un asiento contable y que "si
         * estuvo mal, se compensa con otro" -- pero la API no dejaba: el monto
         * era @Positive, no hay endpoint para borrar un aporte, y tampoco para
         * reabrir un pozo. Si alguien tipeaba 4000000 en vez de 400000 parado en
         * el aeropuerto, el pozo quedaba con esa plata para siempre y la unica
         * salida era entrar a mano a la base.
         *
         * Para una feature cuyo numero protagonista es "queda $X", eso era un
         * agujero grande.
         *
         * El aporte sigue siendo inmutable: no se edita ni se borra, se
         * compensa. Queda el rastro de los dos asientos, que en contabilidad es
         * lo correcto.
         */
        @NotNull(message = "el monto es obligatorio")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
