package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;

import java.util.List;

/**
 * El grupo con sus integrantes.
 *
 * Existe por un motivo muy concreto del cliente: para poder cargar un gasto
 * COMPARTIDO que pago la otra persona, la app necesita su id, y hasta ahora no
 * tenia ninguna forma de averiguarlo. Sabia quien era ella misma (viene en el
 * login) y nada mas.
 *
 * Los integrantes son {@link UsuarioRespuesta}, o sea id y nombre. NO se expone
 * el email: es un dato de contacto que la app no necesita para nada, y la regla
 * de esta capa es que un DTO lleva lo que hace falta y ni un campo mas.
 */
public record GrupoRespuesta(String id, String nombre, List<UsuarioRespuesta> integrantes) {

    public static GrupoRespuesta desde(Grupo grupo, List<Usuario> integrantes) {
        return new GrupoRespuesta(
                grupo.getId(),
                grupo.getNombre(),
                integrantes.stream().map(UsuarioRespuesta::desde).toList());
    }
}
