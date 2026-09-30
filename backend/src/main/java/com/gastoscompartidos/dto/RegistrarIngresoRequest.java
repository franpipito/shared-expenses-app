package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

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
 * SE ADMITE NEGATIVO, mismo motivo que {@link AporteRequest}: es la forma de
 * corregir un ingreso mal cargado, con un asiento en contrario. Cero se
 * rechaza en el servicio -- no es un ingreso ni una corrección.
 */
public record RegistrarIngresoRequest(
        @NotNull(message = "el monto es obligatorio")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto
) {
}
