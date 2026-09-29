package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.OlvideContrasenaRequest;
import com.gastoscompartidos.dto.RestablecerContrasenaRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.dto.UsuarioRespuesta;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.mail.EnviadorDeMails;
import com.gastoscompartidos.mail.MailNoEnviadoException;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.PoliticaDeContrasenas;
import com.gastoscompartidos.seguridad.ServicioDeTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * "Me olvide la contrasena": un codigo de seis digitos por mail, y con el, una
 * contrasena nueva.
 *
 * Codigo y no link, a proposito: un link tendria que abrir la app, y muchos
 * clientes de mail bloquean los esquemas propios (minutria://). Hacerlo bien
 * exige Universal Links, que piden un dominio propio. El codigo se tipea en la
 * app y anda con cualquier cliente.
 *
 * LAS REGLAS, cada una por un ataque concreto:
 *
 *  - **El pedido responde lo mismo exista o no el email.** Si dijera "ese email
 *    no esta registrado", seria un verificador de cuentas. (El registro todavia
 *    lo revela -- es un riesgo conocido, ver CLAUDE.md -- pero no hace falta
 *    abrir un segundo agujero.)
 *  - **El codigo vence a los 15 minutos y acepta 5 intentos.** Son un millon de
 *    combinaciones: con intentos ilimitados se adivina. Con 5, la chance de
 *    acertar al azar es 1 en 200.000 por codigo pedido.
 *  - **Pedir codigos tambien tiene tope**, por email y por IP: si no, alguien
 *    podria inundarle la casilla a una persona, o usar esto para mandar spam
 *    desde la cuenta de Gmail de la app.
 *  - **El codigo se guarda hasheado con BCrypt**, nunca en claro.
 *  - **Restablecer cierra todas las otras sesiones**, porque muchas veces se
 *    resetea justo porque alguien mas entro.
 *  - **Todos los errores del codigo dicen lo mismo** ("no es valido o vencio"):
 *    no se distingue "no existe el email" de "codigo equivocado".
 */
@Service
public class RecuperacionServicio {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionServicio.class);

    static final Duration VIGENCIA = Duration.ofMinutes(15);
    static final int MAX_INTENTOS = 5;
    private static final String CODIGO_INVALIDO =
            "El código no es válido o ya venció. Pedí uno nuevo.";

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final EnviadorDeMails mails;
    private final LimitadorDeIntentos limitador;
    private final ServicioDeTokens tokens;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public RecuperacionServicio(UsuarioRepositorio usuarios,
                                PasswordEncoder codificador,
                                EnviadorDeMails mails,
                                LimitadorDeIntentos limitador,
                                ServicioDeTokens tokens,
                                Clock reloj) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.mails = mails;
        this.limitador = limitador;
        this.tokens = tokens;
        this.reloj = reloj;
    }

    /**
     * Manda un codigo al email, si tiene cuenta. No devuelve nada, ni avisa si no
     * la tiene.
     *
     * Si el mail no sale, se loguea y se sigue: un error distinto revelaria que
     * la cuenta existe. La persona no recibe nada y vuelve a pedir, que es lo
     * mismo que veria si se equivoco de email.
     */
    public void pedirCodigo(OlvideContrasenaRequest req, String ip) {
        String email = normalizar(req.email());

        // Cuentan los pedidos, no los fallos: pedir un codigo siempre "sale
        // bien". El de email es el que protege a una persona de que le inunden
        // la casilla; el de IP, de que se use para barrer emails o mandar spam.
        String porEmail = "reseteo:" + email;
        String porIp = "reseteo-ip:" + ip;
        limitador.verificar(porEmail, LimitadorDeIntentos.MAX_POR_CUENTA);
        limitador.verificar(porIp, LimitadorDeIntentos.MAX_POR_IP);
        limitador.registrarIntento(porEmail);
        limitador.registrarIntento(porIp);

        Optional<Usuario> encontrado = usuarios.findByEmail(email);
        if (encontrado.isEmpty()) {
            return;
        }
        Usuario usuario = encontrado.get();

        // SecureRandom y no Random: el codigo es una credencial. Con ceros a la
        // izquierda, para que siempre sean seis digitos.
        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        usuario.iniciarReseteo(codificador.encode(codigo), Instant.now(reloj).plus(VIGENCIA));
        usuarios.save(usuario);

        try {
            mails.enviar(email, "Tu código de MiNutria: " + codigo, textoDelMail(usuario.getNombre(), codigo));
        } catch (MailNoEnviadoException e) {
            log.error("No salio el mail de reseteo para el usuario {}: {}", usuario.getId(), e.getMessage());
        }
    }

    /**
     * Verifica el codigo y cambia la contrasena. Si sale bien, devuelve un token:
     * la persona queda adentro sin tener que loguearse de nuevo.
     */
    public TokenRespuesta restablecer(RestablecerContrasenaRequest req) {
        String email = normalizar(req.email());
        Usuario usuario = usuarios.findByEmail(email)
                .orElseThrow(() -> new ReglaDeNegocioException(CODIGO_INVALIDO));

        if (usuario.getResetCodigoHash() == null
                || usuario.getResetVence() == null
                || Instant.now(reloj).isAfter(usuario.getResetVence())
                || usuario.getResetIntentos() >= MAX_INTENTOS) {
            throw new ReglaDeNegocioException(CODIGO_INVALIDO);
        }

        if (!codificador.matches(req.codigo().trim(), usuario.getResetCodigoHash())) {
            usuario.registrarIntentoDeReseteo();
            usuarios.save(usuario);
            throw new ReglaDeNegocioException(CODIGO_INVALIDO);
        }

        // La politica va DESPUES del codigo y no gasta intentos: elegir una
        // contrasena corta no es adivinar codigos, y el codigo sigue sirviendo
        // para probar con otra.
        String motivo = PoliticaDeContrasenas.motivoDeRechazo(req.password(), email, usuario.getNombre());
        if (motivo != null) {
            throw new ReglaDeNegocioException(motivo);
        }

        usuario.restablecerContrasena(codificador.encode(req.password()));
        Usuario guardado = usuarios.save(usuario);
        return new TokenRespuesta(
                tokens.emitirPara(guardado.getId(), guardado.getTokenVersion()),
                Instant.now(reloj).plus(tokens.duracion()),
                UsuarioRespuesta.desde(guardado));
    }

    private static String textoDelMail(String nombre, String codigo) {
        return "Hola, " + nombre + ".\n\n"
                + "Tu código para cambiar la contraseña de MiNutria es:\n\n"
                + "    " + codigo + "\n\n"
                + "Vence en 15 minutos. Escribilo en la app junto con tu contraseña nueva.\n\n"
                + "Si no lo pediste vos, ignorá este mail: tu contraseña sigue siendo la misma.";
    }

    /** Igual que en el login y el registro: minusculas y sin espacios. */
    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
