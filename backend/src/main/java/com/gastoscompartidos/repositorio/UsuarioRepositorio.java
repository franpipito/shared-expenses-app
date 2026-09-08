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

    /** Los integrantes de un grupo. Hoy son siempre dos. */
    List<Usuario> findByGrupoIdOrderByIdAsc(String grupoId);

    long countByGrupoId(String grupoId);
}
