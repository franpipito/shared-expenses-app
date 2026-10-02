package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
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
 *
 * SIEMPRE POSITIVO: ya no hace falta admitir negativo para corregir un aporte
 * mal cargado -- {@code Aporte} gano {@code id} y se corrige con
 * {@link EditarAporteRequest} o se borra, de verdad. Antes, sin eso, tipear
 * 4.000.000 en vez de 400.000 parado en el aeropuerto dejaba el pozo con esa
 * plata para siempre salvo que se compensara con un asiento en contrario;
 * ahora se toca la fila y se corrige, mismo criterio que ya tiene un ingreso
 * de "Mi Plata" (seccion 2.3c).
 */
public record AporteRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
