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
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.NoAutenticadoException;
import com.gastoscompartidos.seguridad.PoliticaDeContrasenas;
import com.gastoscompartidos.seguridad.ServicioDeTokens;
import com.gastoscompartidos.seguridad.UsuarioActual;
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
    private final LimitadorDeIntentos limitador;
    private final UsuarioActual usuarioActual;
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
                                 LimitadorDeIntentos limitador,
                                 UsuarioActual usuarioActual,
                                 Clock reloj,
                                 @Value("${app.registro.codigo-invitacion}") String codigoInvitacion,
                                 @Value("${app.grupo-por-defecto:Casa}") String nombreGrupoPorDefecto) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.codificador = codificador;
        this.tokens = tokens;
        this.limitador = limitador;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
        this.codigoInvitacion = codigoInvitacion;
        this.nombreGrupoPorDefecto = nombreGrupoPorDefecto;
        this.hashSenuelo = codificador.encode(UUID.randomUUID().toString());
    }

    /**
     * @param ip de donde viene la request. La pasa el controlador para que este
     *           servicio no tenga que saber que existe HTTP.
     */
    @Transactional
    public TokenRespuesta registrar(RegistroRequest req, String ip) {
        // El codigo de invitacion tambien es adivinable a fuerza bruta, y aca
        // no hay email contra el cual limitar: la clave es la IP.
        String clave = "registro:" + ip;
        limitador.verificar(clave, LimitadorDeIntentos.MAX_POR_IP);

        if (!codigoInvitacion.equals(req.codigoInvitacion())) {
            limitador.registrarFallo(clave);
            throw new ReglaDeNegocioException("El codigo de invitacion no es valido");
        }

        String email = normalizar(req.email());

        String motivo = PoliticaDeContrasenas.motivoDeRechazo(req.password(), email, req.nombre());
        if (motivo != null) {
            throw new ReglaDeNegocioException(motivo);
        }

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

        limitador.limpiar(clave);
        return tokenPara(usuarios.save(usuario));
    }

    @Transactional(readOnly = true)
    public TokenRespuesta login(LoginRequest req, String ip) {
        String email = normalizar(req.email());

        // Dos claves, y la del email es la que de verdad protege: para atacar la
        // cuenta de alguien hay que mandar SU email, y eso no se puede falsear.
        // La IP es defensa adicional, pero detras de un proxy depende de un
        // header que si se puede falsear.
        String clavePorEmail = "login:" + email;
        String clavePorIp = "login-ip:" + ip;
        limitador.verificar(clavePorEmail, LimitadorDeIntentos.MAX_POR_CUENTA);
        limitador.verificar(clavePorIp, LimitadorDeIntentos.MAX_POR_IP);

        Optional<Usuario> encontrado = usuarios.findByEmail(email);

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
            limitador.registrarFallo(clavePorEmail);
            limitador.registrarFallo(clavePorIp);
            // El MISMO mensaje para los dos casos. Decir "ese email no existe"
            // convertiria el login en un verificador de emails registrados.
            throw new NoAutenticadoException("Email o contrasena incorrectos");
        }

        limitador.limpiar(clavePorEmail);
        limitador.limpiar(clavePorIp);
        return tokenPara(encontrado.get());
    }

    /**
     * Cierra TODAS las sesiones del usuario, incluida la que hace esta llamada.
     *
     * Es la respuesta a "perdi el celular": sube la generacion de tokens y, a
     * partir del proximo request, cualquier JWT emitido antes se rechaza aunque
     * su firma siga siendo valida y no haya expirado.
     *
     * Devuelve un token nuevo para que quien lo pidio desde otro dispositivo no
     * quede afuera de su propia sesion.
     */
    @Transactional
    public TokenRespuesta cerrarOtrasSesiones() {
        Usuario usuario = usuarioActual.requerido();
        usuario.invalidarSesiones();
        usuarios.flush();
        return tokenPara(usuario);
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
                tokens.emitirPara(usuario.getId(), usuario.getTokenVersion()),
                Instant.now(reloj).plus(tokens.duracion()),
                UsuarioRespuesta.desde(usuario));
    }

    /** Los emails se guardan y se comparan en minusculas y sin espacios. */
    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
