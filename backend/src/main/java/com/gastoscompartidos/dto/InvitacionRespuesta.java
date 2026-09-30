package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Grupo;

import java.time.Instant;

/** El codigo recien generado, para mostrarlo y compartirlo. */
public record InvitacionRespuesta(String codigo, Instant vence) {

    public static InvitacionRespuesta desde(Grupo grupo) {
        return new InvitacionRespuesta(grupo.getInvitacionCodigo(), grupo.getInvitacionVence());
    }
}
