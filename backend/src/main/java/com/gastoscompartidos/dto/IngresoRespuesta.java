package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Ingreso;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un ingreso de "Mi Plata", tal como lo ve la API. */
public record IngresoRespuesta(BigDecimal monto, LocalDate fecha) {

    public static IngresoRespuesta desde(Ingreso ingreso) {
        return new IngresoRespuesta(ingreso.monto(), ingreso.fecha());
    }
}
