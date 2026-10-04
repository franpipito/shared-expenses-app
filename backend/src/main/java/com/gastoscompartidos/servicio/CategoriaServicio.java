package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.CrearCategoriaRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Las categorias de gasto, 100% personalizables por grupo (v1.1, seccion 2.5),
 * y SIN ningun juego default (seccion 2.7): un grupo nuevo nace sin ninguna
 * categoria propia.
 *
 * Hasta la seccion 2.5 eran globales: seis categorias compartidas por TODA la
 * base, sembradas una vez al arrancar. La 2.5 las paso a ser por grupo, pero
 * segui sembrando esas mismas seis para cada grupo nuevo -- Franco pidio ir
 * mas lejos: nada de default, ni siquiera por grupo. Un grupo elige las
 * suyas en el mini-onboarding despues de registrarse (`app/onboarding-
 * categorias.tsx`), o las crea sobre la marcha con el "+ Agregar" del
 * formulario de gasto. Las dos pegan contra el mismo `POST /categorias` de
 * siempre -- no hay un camino de alta distinto para "la primera vez".
 *
 * Un grupo puede editar su lista como quiera -- agregar, borrar -- sin
 * tocarle nada a ningun otro.
 */
@Service
public class CategoriaServicio {

    private final CategoriaRepositorio categorias;
    private final UsuarioActual usuarioActual;

    public CategoriaServicio(CategoriaRepositorio categorias, UsuarioActual usuarioActual) {
        this.categorias = categorias;
        this.usuarioActual = usuarioActual;
    }

    public List<CategoriaRespuesta> listar() {
        String grupoId = usuarioActual.requerido().getGrupoId();
        return categorias.findByGrupoIdOrderByNombreAsc(grupoId)
                .stream()
                .map(CategoriaRespuesta::desde)
                .toList();
    }

    /**
     * El nombre se compara sin importar mayusculas/minusculas: "Comida" y
     * "comida" son la misma categoria para quien la esta eligiendo de una
     * lista, aunque el indice unico de la base (case-sensitive) no alcance
     * para verlo. El indice sigue siendo la garantia real contra una carrera
     * entre dos requests casi simultaneas; este chequeo es el mensaje util
     * para el caso comun.
     */
    public CategoriaRespuesta crear(CrearCategoriaRequest req) {
        String grupoId = usuarioActual.requerido().getGrupoId();
        String nombre = req.nombre().trim();

        boolean yaExiste = categorias.findByGrupoIdOrderByNombreAsc(grupoId).stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombre));
        if (yaExiste) {
            throw new ReglaDeNegocioException("Ya tenés una categoría con ese nombre");
        }

        Categoria creada = categorias.save(new Categoria(grupoId, nombre, req.icono()));
        return CategoriaRespuesta.desde(creada);
    }

    /**
     * Cualquier categoria se puede borrar, inclusive una de las seis
     * originales -- Franco pidio que fuera 100% personalizable, no "seis
     * fijas mas lo que agregues". Lo unico que se protege es no dejar el
     * grupo sin NINGUNA: sin eso, el formulario de alta se queda sin un chip
     * para elegir y no se puede cargar un gasto, que es el pecado capital de
     * esta app.
     *
     * Borrar una categoria en uso no rompe nada ya cargado: `Gasto.categoria`
     * es un snapshot embebido (ver `ReferenciaCategoria`), asi que un gasto
     * viejo sigue mostrando su nombre e icono aunque la categoria en si ya no
     * exista.
     */
    public void borrar(String id) {
        String grupoId = usuarioActual.requerido().getGrupoId();

        if (categorias.countByGrupoId(grupoId) <= 1) {
            throw new ReglaDeNegocioException("No podés borrar tu última categoría");
        }

        long borradas = categorias.deleteByIdAndGrupoId(id, grupoId);
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("No existe la categoría " + id);
        }
    }
}
