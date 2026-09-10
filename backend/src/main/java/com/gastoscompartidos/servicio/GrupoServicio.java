package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GrupoRespuesta;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

/**
 * El grupo de quien hace la request.
 *
 * NO recibe un id por parametro, y eso es lo importante: el grupo que se
 * devuelve es SIEMPRE el del usuario autenticado. Si el endpoint fuera
 * `GET /grupo/{id}`, habria que validar que ese id sea el suyo -- y esa
 * validacion es justo la que alguien se olvida de escribir algun dia. Un
 * endpoint que no acepta el id no puede filtrar datos de otro grupo, por
 * construccion.
 *
 * Es la misma idea que ya gobierna los gastos: el `grupoId` sale de
 * {@link UsuarioActual}, nunca de la request.
 */
@Service
public class GrupoServicio {

    private final GrupoRepositorio grupos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioActual usuarioActual;

    public GrupoServicio(GrupoRepositorio grupos,
                         UsuarioRepositorio usuarios,
                         UsuarioActual usuarioActual) {
        this.grupos = grupos;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
    }

    public GrupoRespuesta mio() {
        Usuario actual = usuarioActual.requerido();

        Grupo grupo = grupos.findById(actual.getGrupoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el grupo " + actual.getGrupoId()));

        // Ordenado por id ascendente, que en Mongo es orden de creacion: los
        // primeros bytes de un ObjectId son el timestamp. Asi la lista sale
        // siempre igual y el primero es quien creo el grupo. Sin un orden
        // explicito, Mongo no garantiza ninguno.
        return GrupoRespuesta.desde(grupo, usuarios.findByGrupoIdOrderByIdAsc(actual.getGrupoId()));
    }
}
