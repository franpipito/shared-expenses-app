package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Aporte;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un aporte, tal como lo ve la API. */
public record AporteRespuesta(UsuarioRespuesta usuario, BigDecimal monto, LocalDate fecha) {

    public static AporteRespuesta desde(Aporte aporte) {
        return new AporteRespuesta(
                new UsuarioRespuesta(aporte.usuario().usuarioId(), aporte.usuario().nombre()),
                aporte.monto(),
                aporte.fecha());
    }
}
