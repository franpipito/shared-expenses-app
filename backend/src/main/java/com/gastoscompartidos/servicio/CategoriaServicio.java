package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.GuardarCategoriaRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.ReferenciaCategoria;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Las categorias de gasto: cada persona tiene las suyas (seccion 2.9), y nadie
 * nace con ninguna (seccion 2.7).
 *
 * Se eligen en el mini-onboarding despues de registrarse, se crean sobre la
 * marcha con el "+ Agregar" del formulario de gasto, y se editan o borran desde
 * la pantalla "Categorias" del menu. Todo pega contra estos cuatro metodos.
 *
 * Lo que una persona haga con su lista no le toca nada a la otra, aunque
 * compartan grupo: ver `Categoria` para el por que.
 */
@Service
public class CategoriaServicio {

    private final CategoriaRepositorio categorias;
    private final GastoRepositorio gastos;
    private final UsuarioActual usuarioActual;

    public CategoriaServicio(CategoriaRepositorio categorias,
                             GastoRepositorio gastos,
                             UsuarioActual usuarioActual) {
        this.categorias = categorias;
        this.gastos = gastos;
        this.usuarioActual = usuarioActual;
    }

    public List<CategoriaRespuesta> listar() {
        String yo = usuarioActual.requerido().getId();
        return categorias.findByUsuarioIdOrderByNombreAsc(yo)
                .stream()
                .map(CategoriaRespuesta::desde)
                .toList();
    }

    public CategoriaRespuesta crear(GuardarCategoriaRequest req) {
        String yo = usuarioActual.requerido().getId();
        String nombre = req.nombre().trim();

        exigirNombreLibre(yo, nombre, null);

        Categoria creada = categorias.save(new Categoria(yo, nombre, req.icono()));
        return CategoriaRespuesta.desde(creada);
    }

    /**
     * Cambiar el nombre o el icono, Y PROPAGARLO a los gastos ya cargados.
     *
     * Lo segundo es la decision de esta seccion. Los gastos llevan un snapshot
     * de la categoria, asi que sin propagar, renombrar "cafe" a "cafeteria"
     * dejaria los gastos viejos como "cafe" y "Por categoria" mostraria dos
     * filas para lo que la persona considera una sola cosa. Renombrar casi
     * siempre es corregir, no fundar una categoria nueva.
     *
     * EL ORDEN: primero los gastos, despues la categoria. Son dos escrituras
     * sin transaccion; si la segunda falla, la categoria conserva el nombre
     * viejo y reintentar la edicion vuelve a matchear todo (por id, y los que
     * falten por nombre). Al reves, un fallo en el medio dejaria la categoria
     * renombrada y el reintento ya no sabria cual era el nombre viejo.
     */
    public CategoriaRespuesta editar(String id, GuardarCategoriaRequest req) {
        Usuario actual = usuarioActual.requerido();
        String yo = actual.getId();

        Categoria categoria = categorias.findByIdAndUsuarioId(id, yo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría " + id));

        String nombre = req.nombre().trim();
        exigirNombreLibre(yo, nombre, id);

        gastos.actualizarCategoria(actual.getGrupoId(), yo, categoria.getNombre(),
                new ReferenciaCategoria(id, nombre, req.icono()));

        categoria.setNombre(nombre);
        categoria.setIcono(req.icono());
        return CategoriaRespuesta.desde(categorias.save(categoria));
    }

    /**
     * Cualquier categoria se puede borrar. Lo unico que se protege es no
     * quedarse sin NINGUNA: sin eso, el formulario de alta se queda sin un chip
     * para elegir y no se puede cargar un gasto, que es el pecado capital de
     * esta app.
     *
     * Borrar NO toca los gastos ya cargados, a diferencia de editar: conservan
     * el nombre y el icono por el snapshot. Se evaluo pasarlos a "otros", pero
     * desde la 2.7 nadie tiene garantizada una categoria "otros" a la cual
     * mandarlos, y el snapshot ya dice la verdad: asi se llamaba cuando se
     * cargo.
     */
    public void borrar(String id) {
        String yo = usuarioActual.requerido().getId();

        if (categorias.countByUsuarioId(yo) <= 1) {
            throw new ReglaDeNegocioException("No podés borrar tu última categoría");
        }

        long borradas = categorias.deleteByIdAndUsuarioId(id, yo);
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("No existe la categoría " + id);
        }
    }

    /**
     * El nombre se compara sin importar mayusculas/minusculas: "Comida" y
     * "comida" son la misma categoria para quien la elige de una lista, aunque
     * el indice unico de la base (case-sensitive) no alcance para verlo. El
     * indice sigue siendo la garantia real contra una carrera entre dos
     * requests casi simultaneas; este chequeo es el mensaje util para el caso
     * comun.
     *
     * @param salvoId al editar, la propia categoria no cuenta como repetida:
     *                cambiarle solo el icono, o "cafe" por "Cafe", tiene que
     *                poder hacerse. Null al crear.
     */
    private void exigirNombreLibre(String usuarioId, String nombre, String salvoId) {
        boolean yaExiste = categorias.findByUsuarioIdOrderByNombreAsc(usuarioId).stream()
                .filter(c -> !c.getId().equals(salvoId))
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombre));
        if (yaExiste) {
            throw new ReglaDeNegocioException("Ya tenés una categoría con ese nombre");
        }
    }
}
