package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Categoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de categorias.
 *
 * Todo esta escopeado por USUARIO (seccion 2.9): no hay un "listar todas" ni un
 * "las del grupo". El chequeo de nombre repetido es case-insensitive (ver
 * `CategoriaServicio`), asi que no hay un `findByUsuarioIdAndNombre` -- una
 * consulta derivada de Spring Data ahi seria exacta, no alcanzaria.
 */
public interface CategoriaRepositorio extends MongoRepository<Categoria, String> {

    List<Categoria> findByUsuarioIdOrderByNombreAsc(String usuarioId);

    /**
     * Usado por `GastoServicio` al cargar un gasto y por `CategoriaServicio` al
     * editar: NO alcanza un `findById` a secas. Sin el filtro por duenio,
     * cualquiera podria usar (o renombrar) la categoria de otra persona
     * conociendo su id.
     */
    Optional<Categoria> findByIdAndUsuarioId(String id, String usuarioId);

    /** Para no dejar a alguien sin ninguna categoria al borrar la ultima. */
    long countByUsuarioId(String usuarioId);

    /**
     * Borra solo si es DE ESA PERSONA. El filtro en la misma operacion es lo que
     * hace que tocar el id de otro de 404 y no 204 -- mismo patron que
     * `LiquidacionRepositorio.deleteByIdAndGrupoId`.
     */
    long deleteByIdAndUsuarioId(String id, String usuarioId);

    /**
     * Usado por `CuentaServicio`: al borrar la cuenta se van sus categorias,
     * siempre. Cuando eran del grupo solo se borraban si se iba el ultimo
     * integrante; ahora no hay nadie mas a quien le sirvan.
     */
    long deleteByUsuarioId(String usuarioId);
}
