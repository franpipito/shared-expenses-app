package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GrupoRespuesta;
import com.gastoscompartidos.dto.InvitacionRespuesta;
import com.gastoscompartidos.dto.SalirDelGrupoRequest;
import com.gastoscompartidos.dto.SumarseRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * El grupo de quien hace la request: leerlo, invitar a alguien, sumarse con un
 * codigo, o salir.
 *
 * NO recibe un id por parametro en ningun metodo, y eso es lo importante: el
 * grupo sobre el que se actua es SIEMPRE el del usuario autenticado. Ver el
 * javadoc que ya tenia {@link #mio()}.
 */
@Service
public class GrupoServicio {

    /**
     * Tope de integrantes por grupo. Con mas de dos, el reparto (montoPagador),
     * el saldo ("quien le debe a quien") y la UI del alta dejan de tener
     * sentido tal como estan -- eso es la v1.1.2, un cambio de modelo mucho mas
     * grande que este. Por ahora, invitar y sumarse se rechazan si el grupo ya
     * tiene a las dos personas.
     */
    static final int MAX_INTEGRANTES = 2;

    /**
     * Cuanto vive un codigo de invitacion antes de volverse inutilizable.
     *
     * Un codigo sin vencimiento que quede dando vueltas en un chat viejo es una
     * llave abierta para siempre. Siete dias alcanza para coordinar con una
     * persona real; comparalo con los 15 minutos del codigo de reseteo, que es
     * mucho mas corto porque ese protege una cuenta y no una invitacion.
     */
    static final Duration VIGENCIA_INVITACION = Duration.ofDays(7);

    /**
     * Alfabeto sin 0/O ni 1/I/L: son los caracteres que mas se confunden al
     * leerlos en voz alta o copiarlos a mano desde un chat. 32 caracteres y 8
     * de largo (ver generarCodigo) son ~2^40 combinaciones -- adivinar uno a
     * ciegas no es viable ni con el rate limit de por si desactivado.
     */
    private static final String ALFABETO_CODIGO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LARGO_CODIGO = 8;

    private final GrupoRepositorio grupos;
    private final UsuarioRepositorio usuarios;
    private final GastoRepositorio gastos;
    private final PasswordEncoder codificador;
    private final LimitadorDeIntentos limitador;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;
    private final String nombreGrupoPorDefecto;
    private final SecureRandom azar = new SecureRandom();

    public GrupoServicio(GrupoRepositorio grupos,
                         UsuarioRepositorio usuarios,
                         GastoRepositorio gastos,
                         PasswordEncoder codificador,
                         LimitadorDeIntentos limitador,
                         UsuarioActual usuarioActual,
                         Clock reloj,
                         @Value("${app.grupo-por-defecto:Casa}") String nombreGrupoPorDefecto) {
        this.grupos = grupos;
        this.usuarios = usuarios;
        this.gastos = gastos;
        this.codificador = codificador;
        this.limitador = limitador;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
        this.nombreGrupoPorDefecto = nombreGrupoPorDefecto;
    }

    public GrupoRespuesta mio() {
        Usuario actual = usuarioActual.requerido();
        Grupo grupo = buscarGrupo(actual.getGrupoId());
        return respuesta(grupo);
    }

    /**
     * Genera un codigo de invitacion nuevo para el grupo de quien pregunta.
     * Si ya habia uno vigente, esta llamada lo reemplaza: el viejo deja de
     * servir (es la forma en que "regenerar" invalida al anterior, sin
     * necesitar una regla aparte).
     */
    public InvitacionRespuesta invitar() {
        Usuario actual = usuarioActual.requerido();

        if (usuarios.countByGrupoId(actual.getGrupoId()) >= MAX_INTEGRANTES) {
            throw new ReglaDeNegocioException("Tu grupo ya tiene a las dos personas");
        }

        Grupo grupo = buscarGrupo(actual.getGrupoId());
        grupo.generarInvitacion(generarCodigo(), Instant.now(reloj).plus(VIGENCIA_INVITACION));
        return InvitacionRespuesta.desde(grupos.save(grupo));
    }

    /**
     * Se suma al grupo dueño de ese codigo. Sus gastos PERSONAL se mudan con
     * ella -- siguen siendo suyos y privados, sección 2.1 de
     * docs/proxima-sesion.md -- y su grupo de uno, ahora vacío, se borra.
     */
    public GrupoRespuesta sumarse(SumarseRequest req) {
        Usuario actual = usuarioActual.requerido();

        // No tiene sentido sumarse a otro grupo estando ya de a dos: primero
        // hay que salir del propio. Uno a la vez evita tener que pensar que
        // pasa con los gastos COMPARTIDO que ya existian con la pareja actual.
        if (usuarios.tienePareja(actual.getGrupoId())) {
            throw new ReglaDeNegocioException(
                    "Ya estás en un grupo compartido; salí de ahí antes de sumarte a otro");
        }

        // Por cuenta, como borrar-cuenta: con un token robado, esto seria un
        // adivinador de codigos sin limite (la entropia del codigo ya lo hace
        // inviable, pero es defensa en profundidad barata).
        String clave = "sumarse:" + actual.getId();
        limitador.verificar(clave, LimitadorDeIntentos.MAX_POR_CUENTA);

        String codigo = req.codigo().trim().toUpperCase();
        Grupo destino = grupos.consumirInvitacion(codigo, Instant.now(reloj))
                .orElseGet(() -> {
                    limitador.registrarFallo(clave);
                    throw new ReglaDeNegocioException("Ese código no es válido o venció");
                });

        String grupoViejoId = actual.getGrupoId();
        gastos.moverPersonalesA(grupoViejoId, actual.getId(), destino.getId());
        actual.setGrupoId(destino.getId());
        usuarios.save(actual);

        // Al final, y sin problema si falla: un grupo vacio que nadie ve es el
        // mismo riesgo aceptado que el huerfano del registro (CLAUDE.md).
        grupos.deleteById(grupoViejoId);

        limitador.limpiar(clave);
        return respuesta(destino);
    }

    /**
     * Deja el grupo compartido. Simetrica: cualquiera de los dos puede salir
     * cuando quiera, sin permiso de la otra persona -- ver la discusion de
     * 2.1 en docs/proxima-sesion.md sobre por que NO hay una accion separada
     * para "sacar" a alguien.
     *
     * Reusa el patron de CuentaServicio.borrar(): password para confirmar,
     * rate limit por cuenta. La diferencia de fondo es que aca la cuenta
     * sigue existiendo, asi que en vez de borrar o anonimizar, se muda: se
     * crea un grupo propio nuevo (igual que al registrarse) y los PERSONAL se
     * van con la persona. Los COMPARTIDO y los aportes a la vaquita que ya
     * existian quedan tal cual en el grupo viejo, con el nombre real: a
     * diferencia del borrado de cuenta, aca no hay ninguna cuenta que dejo de
     * existir, asi que no hay nada que anonimizar.
     */
    public GrupoRespuesta salir(SalirDelGrupoRequest req) {
        Usuario actual = usuarioActual.requerido();

        String clave = "salir-grupo:" + actual.getId();
        limitador.verificar(clave, LimitadorDeIntentos.MAX_POR_CUENTA);

        if (!codificador.matches(req.password(), actual.getPasswordHash())) {
            limitador.registrarFallo(clave);
            // 400 y no 401, mismo motivo que borrar-cuenta: equivocarse la
            // contrasena al confirmar no puede cerrar la sesion sola.
            throw new ReglaDeNegocioException("La contraseña no es correcta");
        }

        if (!usuarios.tienePareja(actual.getGrupoId())) {
            throw new ReglaDeNegocioException("No hay nadie más en tu grupo; no hay de qué salir");
        }

        Grupo nuevo = grupos.save(new Grupo(nombreGrupoPorDefecto));
        gastos.moverPersonalesA(actual.getGrupoId(), actual.getId(), nuevo.getId());
        actual.setGrupoId(nuevo.getId());
        usuarios.save(actual);

        limitador.limpiar(clave);
        return respuesta(nuevo);
    }

    // ---------------------------------------------------------------- helpers

    private Grupo buscarGrupo(String grupoId) {
        return grupos.findById(grupoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el grupo " + grupoId));
    }

    private GrupoRespuesta respuesta(Grupo grupo) {
        // Ordenado por id ascendente, que en Mongo es orden de creacion: los
        // primeros bytes de un ObjectId son el timestamp. Asi el primero es
        // siempre quien creo el grupo (o quien ya estaba, para sumarse/salir).
        return GrupoRespuesta.desde(grupo, usuarios.findByGrupoIdOrderByIdAsc(grupo.getId()));
    }

    private String generarCodigo() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(ALFABETO_CODIGO.charAt(azar.nextInt(ALFABETO_CODIGO.length())));
        }
        return codigo.toString();
    }
}
