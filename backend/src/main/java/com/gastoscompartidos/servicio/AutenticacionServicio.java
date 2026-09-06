package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.LoginRequest;
import com.gastoscompartidos.dto.RegistroRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.dto.UsuarioRespuesta;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.NoAutenticadoException;
import com.gastoscompartidos.seguridad.ServicioDeTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacionServicio {

    private static final int MAXIMO_INTEGRANTES = 2;

    private final UsuarioRepositorio usuarios;
    private final GrupoRepositorio grupos;
    private final PasswordEncoder codificador;
    private final ServicioDeTokens tokens;
    private final Clock reloj;
    private final String codigoInvitacion;
    private final String nombreGrupoPorDefecto;

    /**
     * Un hash valido de una contrasena aleatoria que nadie conoce. Se usa para
     * el login de emails inexistentes; ver el comentario en `login`.
     */
    private final String hashSenuelo;

    public AutenticacionServicio(UsuarioRepositorio usuarios,
                                 GrupoRepositorio grupos,
                                 PasswordEncoder codificador,
                                 ServicioDeTokens tokens,
                                 Clock reloj,
                                 @Value("${app.registro.codigo-invitacion}") String codigoInvitacion,
                                 @Value("${app.grupo-por-defecto:Casa}") String nombreGrupoPorDefecto) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.codificador = codificador;
        this.tokens = tokens;
        this.reloj = reloj;
        this.codigoInvitacion = codigoInvitacion;
        this.nombreGrupoPorDefecto = nombreGrupoPorDefecto;
        this.hashSenuelo = codificador.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenRespuesta registrar(RegistroRequest req) {
        if (!codigoInvitacion.equals(req.codigoInvitacion())) {
            throw new ReglaDeNegocioException("El codigo de invitacion no es valido");
        }

        String email = normalizar(req.email());
        if (usuarios.findByEmail(email).isPresent()) {
            throw new ReglaDeNegocioException("Ese email ya esta registrado");
        }

        Usuario usuario = new Usuario(
                req.nombre().trim(),
                email,
                // La contrasena en texto plano no se guarda, ni se loguea, ni
                // sale de este metodo. Lo unico que persiste es el hash.
                codificador.encode(req.password()),
                grupoParaNuevoIntegrante());

        return tokenPara(usuarios.save(usuario));
    }

    @Transactional(readOnly = true)
    public TokenRespuesta login(LoginRequest req) {
        Optional<Usuario> encontrado = usuarios.findByEmail(normalizar(req.email()));

        // Se verifica SIEMPRE contra un hash, exista o no el email.
        //
        // Si saliéramos temprano cuando el email no existe, un login fallido
        // tardaria ~1ms y uno con email real ~100ms (BCrypt es lento a
        // proposito). Esa diferencia de tiempo es medible desde afuera, y
        // alcanza para averiguar que emails estan registrados sin conocer
        // ninguna contrasena. Gastar los 100ms siempre cierra ese canal.
        String hash = encontrado.map(Usuario::getPasswordHash).orElse(hashSenuelo);
        boolean coincide = codificador.matches(req.password(), hash);

        if (encontrado.isEmpty() || !coincide) {
            // El MISMO mensaje para los dos casos. Decir "ese email no existe"
            // convertiria el login en un verificador de emails registrados.
            throw new NoAutenticadoException("Email o contrasena incorrectos");
        }

        return tokenPara(encontrado.get());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * El primero que se registra crea el grupo; el segundo se suma al mismo. Un
     * tercero se rechaza, porque el reparto del modelo asume dos integrantes.
     */
    private Grupo grupoParaNuevoIntegrante() {
        Optional<Grupo> existente = grupos.findFirstByOrderByIdAsc();
        if (existente.isEmpty()) {
            return grupos.save(new Grupo(nombreGrupoPorDefecto));
        }
        Grupo grupo = existente.get();
        if (usuarios.findByGrupoIdOrderById(grupo.getId()).size() >= MAXIMO_INTEGRANTES) {
            throw new ReglaDeNegocioException("El grupo ya esta completo");
        }
        return grupo;
    }

    private TokenRespuesta tokenPara(Usuario usuario) {
        return new TokenRespuesta(
                tokens.emitirPara(usuario.getId()),
                Instant.now(reloj).plus(tokens.duracion()),
                UsuarioRespuesta.desde(usuario));
    }

    /** Los emails se guardan y se comparan en minusculas y sin espacios. */
    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
