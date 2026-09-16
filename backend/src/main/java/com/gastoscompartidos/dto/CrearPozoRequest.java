package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que la API recibe para abrir un pozo.
 *
 * Solo el nombre es obligatorio. Todo lo demas se puede completar despues, y esa
 * es una decision de producto: abrir la vaquita tiene que costar un tap, no un
 * formulario. Si abrirla es tramite, no la abren.
 *
 * @param objetivo cuanto se proponen juntar. **No es un tope**: nada se rechaza
 *                 por pasarlo. Sirve para dibujar cuanto falta. Los presupuestos
 *                 estan descartados y esto no es uno -- ver docs/vaquita.md.
 * @param desde    y hasta, las fechas del viaje. Su unico uso es de UX: si hoy
 *                 cae adentro, el alta de gasto abre con "Vaquita" puesto. Se
 *                 admiten futuras, al reves que la fecha de un gasto: un viaje
 *                 se planea antes.
 */
public record CrearPozoRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 60, message = "el nombre no puede pasar de 60 caracteres")
        String nombre,

        @DecimalMin(value = "0.01", message = "el objetivo tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el objetivo es demasiado grande")
        BigDecimal objetivo,

        LocalDate desde,

        LocalDate hasta
) {
}
