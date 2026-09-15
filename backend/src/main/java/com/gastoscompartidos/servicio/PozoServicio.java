package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.AporteRequest;
import com.gastoscompartidos.dto.AporteRespuesta;
import com.gastoscompartidos.dto.CrearPozoRequest;
import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.PozoRespuesta;
import com.gastoscompartidos.dto.TotalPorPersona;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Aporte;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Pozo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * La vaquita: abrir un pozo, aportarle plata, cerrarlo y leer sus numeros.
 *
 * EL INVARIANTE que sostiene todo esto:
 *
 *     restante = suma(aportes) - suma(gastos con pozoId)
 *
 * Los tres numeros se calculan **en cada lectura** y no se guardan en ningun
 * lado, igual que el resumen y el saldo. Es la misma decision que el CLAUDE.md
 * justifica para los agregados mensuales, y aca el caso es todavia mas facil:
 * un viaje son decenas de documentos, no miles.
 *
 * Igual que el resto de los servicios, no hay @Transactional: cada metodo
 * escribe un solo documento, y en Mongo eso ya es atomico.
 */
@Service
public class PozoServicio {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private final PozoRepositorio pozos;
    private final GastoRepositorio gastos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    public PozoServicio(PozoRepositorio pozos,
                        GastoRepositorio gastos,
                        UsuarioRepositorio usuarios,
                        UsuarioActual usuarioActual,
                        Clock reloj) {
        this.pozos = pozos;
        this.gastos = gastos;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    public PozoRespuesta crear(CrearPozoRequest req) {
        Usuario actual = usuarioActual.requerido();

        if (req.desde() != null && req.hasta() != null && req.hasta().isBefore(req.desde())) {
            throw new ReglaDeNegocioException("La fecha de fin no puede ser anterior a la de inicio");
        }

        // Chequeo amable, para poder dar un mensaje que diga algo. NO es la
        // garantia: entre este if y el save() hay una ventana donde dos
        // requests simultaneas pasarian las dos. La garantia de verdad es el
        // indice parcial unico de Pozo, y por eso abajo se atrapa la
        // DuplicateKeyException. El if da el buen mensaje; el indice da la
        // correctitud.
        if (buscarAbierto(actual.getGrupoId()).isPresent()) {
            throw new ReglaDeNegocioException(
                    "Ya hay una vaquita abierta. Cerra la anterior antes de abrir otra.");
        }

        Pozo pozo = new Pozo(
                actual.getGrupoId(),
                req.nombre().trim(),
                req.objetivo() != null ? normalizar(req.objetivo()) : null,
                req.desde(),
                req.hasta());

        try {
            return respuesta(pozos.save(pozo));
        } catch (DuplicateKeyException e) {
            // El indice parcial unico hizo su trabajo: alguien creo un pozo
            // abierto entre nuestro chequeo y nuestra escritura.
            throw new ReglaDeNegocioException(
                    "Ya hay una vaquita abierta. Cerra la anterior antes de abrir otra.");
        }
    }

    /** El pozo abierto del grupo, o vacio si no hay ninguno. */
    public Optional<PozoRespuesta> activo() {
        Usuario actual = usuarioActual.requerido();
        return buscarAbierto(actual.getGrupoId()).map(this::respuesta);
    }

    public PozoRespuesta aportar(String pozoId, AporteRequest req) {
        Usuario actual = usuarioActual.requerido();

        // El aporte se registra a nombre de quien hace la request, nunca de la
        // otra persona. Un aporte es la afirmacion "puse esta plata": solo la
        // puede emitir quien la puso. Por eso AporteRequest ni siquiera tiene
        // un campo para el usuario.
        Aporte aporte = new Aporte(
                actual.comoReferencia(),
                normalizar(req.monto()),
                LocalDate.now(reloj));

        // Una sola operacion atomica que ademas verifica grupo y estado. Si
        // devuelve false no sabemos cual de las tres condiciones fallo, y esta
        // bien: distinguirlas confirmaria que el pozo existe.
        if (!pozos.agregarAporte(pozoId, actual.getGrupoId(), aporte)) {
            throw new RecursoNoEncontradoException("No existe una vaquita abierta con ese id");
        }

        return respuesta(recargar(pozoId, actual.getGrupoId()));
    }

    public PozoRespuesta cerrar(String pozoId) {
        Usuario actual = usuarioActual.requerido();

        if (!pozos.cerrar(pozoId, actual.getGrupoId())) {
            throw new RecursoNoEncontradoException("No existe una vaquita abierta con ese id");
        }

        return respuesta(recargar(pozoId, actual.getGrupoId()));
    }

    /**
     * Los gastos de un pozo, sin recorte por mes.
     *
     * Es el primer listado de la app que no se corta mensualmente, y es
     * deliberado: el pozo ES el recorte. El viaje a Bariloche cruza de
     * septiembre a octubre, asi que cualquier vista mensual lo partiria al medio.
     */
    public List<GastoRespuesta> gastosDe(String pozoId) {
        Usuario actual = usuarioActual.requerido();

        // Se valida que el pozo exista y sea del grupo ANTES de listar: si no,
        // un id cualquiera devolveria una lista vacia y no un 404, que confunde
        // "no hay gastos" con "no existe esa vaquita".
        buscarDelGrupo(pozoId, actual.getGrupoId());

        return gastos.buscarDelPozo(pozoId, actual.getGrupoId()).stream()
                .map(GastoRespuesta::desde)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private Optional<Pozo> buscarAbierto(String grupoId) {
        return pozos.findByGrupoIdAndEstado(grupoId, EstadoPozo.ABIERTO);
    }

    private Pozo buscarDelGrupo(String pozoId, String grupoId) {
        return pozos.findByIdAndGrupoId(pozoId, grupoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la vaquita " + pozoId));
    }

    /**
     * Relee el pozo despues de una escritura condicional.
     *
     * Hace falta porque `agregarAporte` y `cerrar` no devuelven el documento
     * actualizado, solo si matchearon. Es una lectura de mas a cambio de que la
     * escritura sea atomica, y a esta escala no se discute.
     */
    private Pozo recargar(String pozoId, String grupoId) {
        return buscarDelGrupo(pozoId, grupoId);
    }

    private PozoRespuesta respuesta(Pozo pozo) {
        BigDecimal aportado = pozo.totalAportado();
        BigDecimal gastado = gastos.sumarDelPozo(pozo.getId());

        return new PozoRespuesta(
                pozo.getId(),
                pozo.getNombre(),
                pozo.getObjetivo(),
                pozo.getEstado(),
                pozo.getDesde(),
                pozo.getHasta(),
                pozo.vigenteEl(LocalDate.now(reloj)),
                aportado,
                gastado,
                pozo.restante(gastado),
                totalesPorPersona(pozo),
                pozo.getAportes().stream().map(AporteRespuesta::desde).toList(),
                pozo.getVersion());
    }

    /**
     * Cuanto puso cada integrante del grupo, incluidos los que pusieron cero.
     *
     * Se listan los dos y no solo los que aportaron: "Viole $0" es informacion
     * util -- es justamente lo que hay que mirar antes de salir de viaje.
     */
    private List<TotalPorPersona> totalesPorPersona(Pozo pozo) {
        return usuarios.findByGrupoIdOrderByIdAsc(pozo.getGrupoId()).stream()
                .map(u -> new TotalPorPersona(
                        u.getId(), u.getNombre(), pozo.aportadoPor(u.getId())))
                .toList();
    }

    /** El cliente podria mandar 3 decimales. Los llevamos a 2 en el borde. */
    private BigDecimal normalizar(BigDecimal monto) {
        return monto.setScale(ESCALA_DINERO, REDONDEO);
    }
}
