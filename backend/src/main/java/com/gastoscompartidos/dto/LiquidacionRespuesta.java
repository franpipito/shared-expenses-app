package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Liquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una liquidación, tal como la ve la API. */
public record LiquidacionRespuesta(
        String id,
        UsuarioRespuesta de,
        UsuarioRespuesta para,
        BigDecimal monto,
        LocalDate fecha
) {

    public static LiquidacionRespuesta desde(Liquidacion liquidacion) {
        return new LiquidacionRespuesta(
                liquidacion.getId(),
                new UsuarioRespuesta(liquidacion.getDe().usuarioId(), liquidacion.getDe().nombre()),
                new UsuarioRespuesta(liquidacion.getPara().usuarioId(), liquidacion.getPara().nombre()),
                liquidacion.getMonto(),
                liquidacion.getFecha());
    }
}
