package com.gastoscompartidos.seguridad;

import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * La implementacion real de {@link UsuarioActual}: saca el id del
 * SecurityContext que dejo {@link FiltroJwt} y carga el usuario.
 *
 * Reemplaza a UsuarioActualPorHeader, que se borro.
 *
 * ESTE ARCHIVO ES TODO LO QUE CAMBIO al pasar de header a JWT. Ni GastoServicio,
 * ni ResumenServicio, ni un solo controlador se tocaron: dependian de la
 * interfaz y no de como se averigua la identidad. Eso es lo que compramos en la
 * sesion 2 cuando creamos la costura en vez de pasar un usuarioId por parametro.
 */
@Component
public class UsuarioActualPorJwt implements UsuarioActual {

    private final UsuarioRepositorio usuarios;

    public UsuarioActualPorJwt(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Usuario requerido() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        // `instanceof IdentidadDelToken id` es pattern matching (Java 16+):
        // chequea el tipo y declara la variable ya casteada, en un solo paso.
        if (autenticacion == null
                || !(autenticacion.getPrincipal() instanceof IdentidadDelToken identidad)) {
            throw new NoAutenticadoException("No hay un usuario autenticado en esta request");
        }

        // Se carga aca, dentro de la transaccion del servicio, para que la
        // entidad quede managed y las relaciones lazy funcionen.
        Usuario usuario = usuarios.findById(identidad.usuarioId()).orElseThrow(() ->
                new NoAutenticadoException("El token es de un usuario que ya no existe"));

        // ACA SE CIERRA LA REVOCACION. Un JWT con firma valida y sin expirar
        // igual se rechaza si su generacion quedo vieja, que es lo que pasa
        // despues de POST /auth/cerrar-sesiones.
        if (identidad.tokenVersion() != usuario.getTokenVersion()) {
            throw new NoAutenticadoException("La sesion fue cerrada. Volve a entrar.");
        }

        return usuario;
    }
}
