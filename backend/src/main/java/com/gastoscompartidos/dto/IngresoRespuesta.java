package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Ingreso;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un ingreso de "Mi Plata", tal como lo ve la API.
 *
 * Lleva {@code id} (sección 2.3c) porque, a diferencia de un {@code Aporte} o
 * una {@code Liquidacion}, un ingreso se puede editar y borrar de verdad: la
 * app necesita algo para direccionar CUÁL fila tocó la persona.
 */
public record IngresoRespuesta(String id, BigDecimal monto, LocalDate fecha) {

    public static IngresoRespuesta desde(Ingreso ingreso) {
        return new IngresoRespuesta(ingreso.id(), ingreso.monto(), ingreso.fecha());
    }
}
