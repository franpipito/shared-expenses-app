package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Aporte;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un aporte, tal como lo ve la API.
 *
 * Trae {@code id}: el aporte se edita y se borra de verdad, igual que un
 * {@code Ingreso}, y hace falta algo para direccionar CUAL fila del
 * historial se tocó.
 */
public record AporteRespuesta(String id, UsuarioRespuesta usuario, BigDecimal monto, LocalDate fecha) {

    public static AporteRespuesta desde(Aporte aporte) {
        return new AporteRespuesta(
                aporte.id(),
                new UsuarioRespuesta(aporte.usuario().usuarioId(), aporte.usuario().nombre()),
                aporte.monto(),
                aporte.fecha());
    }
}
