package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio extends MongoRepository<Usuario, String> {

    /**
     * Optional en vez de devolver null: obliga a quien llama a contemplar el
     * caso "no existe" en vez de olvidarselo y comerse un NullPointerException.
     * Lo usa el login.
     */
    Optional<Usuario> findByEmail(String email);

    /** Los integrantes de un grupo: uno o dos. */
    List<Usuario> findByGrupoIdOrderByIdAsc(String grupoId);

    long countByGrupoId(String grupoId);

    /**
     * Si el grupo ya tiene a las dos personas.
     *
     * Desde el registro abierto, un grupo puede tener un solo integrante, y ahi
     * no hay con quien compartir: un COMPARTIDO generaria una deuda con nadie y
     * una vaquita seria un pozo de uno. Los servicios lo rechazan preguntando
     * aca, para que la regla viva en un solo lugar.
     *
     * Es un metodo `default`: una interfaz de Java puede traer metodos con
     * cuerpo, y Spring Data los deja pasar tal cual en vez de intentar
     * derivarles una consulta del nombre. Por dentro es un `countByGrupoId`.
     */
    default boolean tienePareja(String grupoId) {
        return countByGrupoId(grupoId) >= 2;
    }
}
