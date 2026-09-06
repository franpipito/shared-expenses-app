package com.gastoscompartidos.seguridad;

import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion PROVISORIA de {@link UsuarioActual}: lee el id de usuario de
 * un header HTTP.
 *
 * Esto NO es autenticacion: cualquiera puede mandar el header que quiera. Sirve
 * para poder construir y probar el CRUD antes de tener JWT. Se reemplaza entero
 * en la sesion 4.
 */
@Component
public class UsuarioActualPorHeader implements UsuarioActual {

    public static final String HEADER = "X-Usuario-Id";

    private final HttpServletRequest request;
    private final UsuarioRepositorio usuarios;

    /**
     * Inyeccion por constructor: Spring ve que este @Component necesita dos
     * cosas y se las pasa al crearlo. Se prefiere sobre @Autowired en un campo
     * porque deja los colaboradores en `final`, hace la clase instanciable en
     * un test sin levantar Spring, y expone si una clase tiene demasiadas
     * dependencias en vez de esconderlo.
     *
     * El HttpServletRequest merece una nota: este bean es un singleton, pero la
     * request cambia en cada llamada. Spring inyecta un PROXY que, cada vez que
     * se lo usa, resuelve la request del hilo actual. Por eso funciona.
     */
    public UsuarioActualPorHeader(HttpServletRequest request, UsuarioRepositorio usuarios) {
        this.request = request;
        this.usuarios = usuarios;
    }

    @Override
    public Usuario requerido() {
        String valor = request.getHeader(HEADER);
        if (valor == null || valor.isBlank()) {
            throw new NoAutenticadoException("Falta el header " + HEADER);
        }

        long id;
        try {
            id = Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            throw new NoAutenticadoException("El header " + HEADER + " tiene que ser un numero");
        }

        return usuarios.findById(id)
                .orElseThrow(() -> new NoAutenticadoException("No existe el usuario " + id));
    }
}
