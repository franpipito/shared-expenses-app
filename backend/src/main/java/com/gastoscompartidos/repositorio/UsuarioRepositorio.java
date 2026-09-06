package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    /**
     * Optional en vez de devolver null: obliga a quien llama a contemplar el
     * caso "no existe" en vez de olvidarselo y comerse un NullPointerException.
     * Lo va a usar el login en la sesion 4.
     */
    Optional<Usuario> findByEmail(String email);

    /** Los integrantes de un grupo. Hoy son siempre dos. */
    List<Usuario> findByGrupoIdOrderById(Long grupoId);
}
