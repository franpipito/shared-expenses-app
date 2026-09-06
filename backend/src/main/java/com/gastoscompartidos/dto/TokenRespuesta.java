package com.gastoscompartidos.dto;

import java.time.Instant;

/**
 * Lo que devuelven el registro y el login.
 *
 * Va el usuario adentro para que el cliente no tenga que hacer una segunda
 * llamada solo para saber como se llama quien acaba de entrar.
 *
 * `expiraEn` viaja explicito aunque el propio token lo lleve adentro: asi la app
 * mobile puede saber cuando pedir un login nuevo sin tener que parsear el JWT.
 */
public record TokenRespuesta(
        String token,
        Instant expiraEn,
        UsuarioRespuesta usuario
) {
}
