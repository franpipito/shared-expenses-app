package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Categoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de categorias.
 *
 * Todo quedo escopeado por grupo (seccion 2.5): ya no hay un "listar todas".
 * El chequeo de nombre repetido es case-insensitive (ver `CategoriaServicio.
 * crear`), asi que no hay un `findByGrupoIdAndNombre` -- una consulta derivada
 * de Spring Data ahi seria exacta, no alcanzaria.
 */
public interface CategoriaRepositorio extends MongoRepository<Categoria, String> {

    List<Categoria> findByGrupoIdOrderByNombreAsc(String grupoId);

    /**
     * Usado por `GastoServicio` al cargar o editar un gasto: con categorias
     * por grupo, ya NO alcanza un `findById` a secas -- sin este filtro,
     * cualquiera podria cargar un gasto propio referenciando el id de una
     * categoria de OTRO grupo (algo que esta sesion encontro revisando el
     * smoke test, no una duda teorica), y el snapshot embebido terminaria
     * mostrando el nombre e icono de una categoria ajena.
     */
    Optional<Categoria> findByIdAndGrupoId(String id, String grupoId);

    /** Para no dejar un grupo sin ninguna categoria al borrar la ultima. */
    long countByGrupoId(String grupoId);

    /**
     * Borra solo si es DE ESE GRUPO. El filtro por grupo en la misma operacion
     * es lo que hace que tocar el id de otro grupo de 404 y no 204 -- mismo
     * patron que `LiquidacionRepositorio.deleteByIdAndGrupoId`.
     */
    long deleteByIdAndGrupoId(String id, String grupoId);

    /**
     * Usado por `CuentaServicio` cuando se va el ULTIMO integrante de un grupo:
     * si el grupo entero se borra, sus categorias quedan sin duenio -- mismo
     * motivo por el que ese mismo caso ya borraba `gastos` y `pozos` del grupo.
     * Antes de la seccion 2.5 esto no hacia falta porque las categorias eran
     * globales, nadie era su "dueno".
     */
    long deleteByGrupoId(String grupoId);
}
