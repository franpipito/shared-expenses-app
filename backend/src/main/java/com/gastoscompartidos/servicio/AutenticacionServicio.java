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

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacionServicio {

    private final UsuarioRepositorio usuarios;
    private final GrupoRepositorio grupos;
    private final PasswordEncoder codificador;
    private final ServicioDeTokens tokens;
    private final LimitadorDeIntentos limitador;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;
    private final String nombreGrupoPorDefecto;

    /**
     * Registros por IP cada 15 minutos, CONTANDO LOS QUE SALEN BIEN. Es el unico
     * limite de la app que cuenta exitos, porque con el registro abierto el
     * exito es justamente el abuso: crear cuentas en serie. Y como el registro
     * contesta "ese email ya esta registrado", cada intento sirve para averiguar
     * si una cuenta existe; este tope es lo que impide barrer una lista.
     *
     * Cinco no molesta a nadie de verdad: una persona se registra una vez en la
     * vida. Es configurable solo para el smoke test, que registra varios
     * usuarios seguidos desde 127.0.0.1 -- el default es el valor seguro, y es el
     * que corre en Render.
     */
    private final int maxRegistrosPorIp;

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
                                 @Value("${app.grupo-por-defecto:Casa}") String nombreGrupoPorDefecto,
                                 @Value("${app.registro.max-por-ip:5}") int maxRegistrosPorIp) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.codificador = codificador;
        this.tokens = tokens;
        this.limitador = limitador;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
        this.nombreGrupoPorDefecto = nombreGrupoPorDefecto;
        this.maxRegistrosPorIp = maxRegistrosPorIp;
        this.hashSenuelo = codificador.encode(UUID.randomUUID().toString());
    }

    /**
     * @param ip de donde viene la request. La pasa el controlador para que este
     *           servicio no tenga que saber que existe HTTP.
     */
    public TokenRespuesta registrar(RegistroRequest req, String ip) {
        // Se cuenta CADA intento, salga bien o mal, y antes de mirar nada. Con el
        // registro abierto el abuso no es fallar: es tener exito muchas veces
        // (cuentas en serie), o preguntar por muchos emails para ver cuales
        // contestan "ya esta registrado". Por eso aca no hay un limpiar()
        // despues del exito, como si lo hay en el login.
        String clave = "registro:" + ip;
        limitador.verificar(clave, maxRegistrosPorIp);
        limitador.registrarIntento(clave);

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
                grupoPropio().getId());

        return tokenPara(usuarios.save(usuario));
    }

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
    public TokenRespuesta cerrarOtrasSesiones() {
        Usuario usuario = usuarioActual.requerido();
        usuario.invalidarSesiones();
        // Antes alcanzaba con usuarios.flush(): la entidad estaba managed y
        // Hibernate detectaba el cambio solo. En Mongo no hay dirty checking,
        // asi que si no se llama a save() el token_version nuevo se pierde al
        // salir del metodo y el boton de "perdi el celular" no hace nada.
        return tokenPara(usuarios.save(usuario));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Cada persona que se registra arranca con un grupo propio, de un solo
     * integrante.
     *
     * Hasta la v1.0 era distinto: el primero creaba EL grupo y el segundo se
     * sumaba, porque habia un unico grupo en toda la base y el codigo de
     * invitacion decidia quien entraba. Con el registro abierto eso significaria
     * que un desconocido que baja la app cae en el grupo de otra pareja. Sumarse
     * al grupo de alguien va a necesitar un codigo POR GRUPO (v1.1), y ahi
     * vuelve el tope de dos integrantes.
     *
     * Con un solo integrante la app es un registro personal: lo compartido y la
     * vaquita se rechazan (ver UsuarioRepositorio.tienePareja).
     *
     * ES EL UNICO LUGAR DE LA APP QUE ESCRIBE DOS DOCUMENTOS sin transaccion. Si
     * se crea el grupo y despues falla el alta del usuario, queda un grupo
     * vacio. Antes el reintento lo reusaba; ahora crea otro, asi que el huerfano
     * queda. Se acepta: es un documento de dos campos que nadie ve. Invertir el
     * orden seria peor, porque dejaria un usuario apuntando a un grupo que no
     * existe, y eso si se nota (`GET /grupo` daria 404).
     */
    private Grupo grupoPropio() {
        return grupos.save(new Grupo(nombreGrupoPorDefecto));
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
