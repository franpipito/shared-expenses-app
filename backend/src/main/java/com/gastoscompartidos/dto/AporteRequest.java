package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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

        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        BigDecimal monto
) {
}
