package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.CrearCategoriaRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las categorias de gasto, 100% personalizables por grupo (v1.1, seccion 2.5).
 *
 * Hasta esta sesion eran globales: seis categorias compartidas por TODA la
 * base, sembradas una vez al arrancar. Con el registro abierto (v1.0) eso ya
 * era raro -- cualquiera que bajara la app desde el App Store heredaba las
 * mismas seis que Franco y Viole -- pero personalizarlas lo vuelve un bug de
 * verdad: si Franco agrega "Netflix", no tiene sentido que le aparezca a un
 * desconocido que se registro ayer, y viceversa.
 *
 * Por eso cada grupo tiene su PROPIA copia, empezando por estas mismas seis
 * ({@link #sembrarParaGrupo}). Un grupo puede editarla como quiera -- agregar,
 * borrar, inclusive las seis originales -- sin tocarle nada a ningun otro.
 */
@Service
public class CategoriaServicio {

    /**
     * Las palabras que uso la usuaria en la entrevista, no las que suponiamos
     * nosotros. Dijo "uber", no "transporte". Ver docs/entrevista-usuaria.md.
     *
     * "otros" no lo nombro ella, pero sin un cajon de sastre un gasto que no
     * encaja en ninguna categoria no se puede cargar, y eso es friccion justo
     * en el peor momento: parada en el mostrador con el pedido listo.
     *
     * El valor es el nombre del icono de Lucide, NO un emoji. Es el mismo
     * mapa que vivia en el viejo `SembradorDeCategorias` global -- movido
     * aca porque ahora se usa en DOS lugares (el registro y el sweep de
     * arranque) y tenerlo en uno solo evita que se desincronicen.
     */
    private static final Map<String, String> DEFAULT = new LinkedHashMap<>(Map.of(
            "cafe", "coffee",
            "uber", "car",
            "comida", "utensils",
            "ropa", "shirt",
            "regalos", "gift",
            "otros", "ellipsis"
    ));

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

    /**
     * Siembra las seis categorias default para un grupo que todavia no tiene
     * ninguna propia. Idempotente por el `existsByGrupoId` de arriba, mismo
     * espiritu que el viejo sembrador global: se puede llamar de mas sin
     * riesgo.
     *
     * Dos llamadores: {@code AutenticacionServicio.grupoPropio()}, para que
     * un grupo nuevo nazca con ellas de una, y el sweep de arranque en
     * {@code SembradorDeCategorias}, que atrapa los grupos que ya existian
     * antes de esta sesion (sin ninguna categoria propia todavia, porque
     * hasta ahora las categorias eran globales).
     */
    public void sembrarParaGrupo(String grupoId) {
        if (categorias.existsByGrupoId(grupoId)) {
            return;
        }
        DEFAULT.forEach((nombre, icono) -> categorias.save(new Categoria(grupoId, nombre, icono)));
    }
}
