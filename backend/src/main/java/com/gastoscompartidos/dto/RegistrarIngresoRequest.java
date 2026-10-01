package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Declarar plata para "Mi Plata" (sección 2.3b).
 *
 * No se pide quién ni de qué grupo: sale del token, igual que un
 * {@link AporteRequest}. Tampoco se pide la fecha, ni un origen o categoría
 * ("me pagaron", "me regalaron", "vendí algo") -- eso es una app de finanzas
 * personales completa, y "Mi Plata" es deliberadamente más chico: solo un
 * número que sube con lo que declarás y baja con tus gastos personales.
 *
 * SIEMPRE POSITIVO (sección 2.3c): a diferencia de {@link AporteRequest}, acá
 * ya no hace falta admitir negativo para corregir un ingreso mal cargado --
 * {@code Ingreso} ganó `id` y se corrige con {@code EditarIngresoRequest} o
 * se borra, de verdad. Probándolo en el teléfono, el asiento en contrario se
 * sintió como vueltas de más para un error de tipeo.
 */
public record RegistrarIngresoRequest(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
