package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Usuario;

/**
 * Un usuario, tal como lo ve la API.
 *
 * Fijate lo que NO esta: `email` y `passwordHash`. Este record es exactamente el
 * motivo por el que no serializamos entidades. Si `GastoRespuesta` expusiera un
 * `Usuario`, el hash de la contrasena saldria por la API en cada listado de
 * gastos, y nadie lo notaria hasta que fuera tarde.
 */
public record UsuarioRespuesta(Long id, String nombre) {

    public static UsuarioRespuesta desde(Usuario usuario) {
        return new UsuarioRespuesta(usuario.getId(), usuario.getNombre());
    }
}
