package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.BalancePersonalRespuesta;
import com.gastoscompartidos.dto.EditarIngresoRequest;
import com.gastoscompartidos.dto.IngresoRespuesta;
import com.gastoscompartidos.dto.RegistrarIngresoRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.modelo.Ingreso;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Mi Plata" (sección 2.3b): {@code restante = ingresado - gastado}, mismo
 * invariante que la vaquita (ver {@code Pozo.restante}), pero para una sola
 * persona y sin fechas de por medio -- no es un viaje, es plata de todos
 * los días.
 *
 * Servicio propio y no una lectura más de {@link ResumenServicio}: mismo
 * criterio que separó {@code PozoServicio} de {@code GastoServicio} y
 * {@code LiquidacionServicio} de {@code ResumenServicio}. Es chico a
 * propósito -- deliberadamente NO es una app de finanzas personales
 * completa (sin cuentas por medio de pago, sin categorías de ingreso, sin
 * gráficos): eso contradiría "todo es plata" (respuesta 11 de la
 * entrevista) y el resto de lo que este proyecto ya dejó fuera de alcance.
 */
@Service
public class BalancePersonalServicio {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private final UsuarioRepositorio usuarios;
    private final GastoRepositorio gastos;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    public BalancePersonalServicio(UsuarioRepositorio usuarios,
                                    GastoRepositorio gastos,
                                    UsuarioActual usuarioActual,
                                    Clock reloj) {
        this.usuarios = usuarios;
        this.gastos = gastos;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    public BalancePersonalRespuesta ver() {
        Usuario actual = usuarioActual.requerido();
        return respuesta(actual);
    }

    public BalancePersonalRespuesta agregarIngreso(RegistrarIngresoRequest req) {
        Usuario actual = usuarioActual.requerido();

        // A diferencia de un Aporte, acá no hace falta un chequeo de cero a
        // mano: @Positive en el DTO ya rechaza cero y negativo antes de que
        // este método se ejecute (sección 2.3c -- ver RegistrarIngresoRequest).
        BigDecimal monto = normalizar(req.monto());
        Ingreso ingreso = new Ingreso(UUID.randomUUID().toString(), monto, LocalDate.now(reloj));
        usuarios.agregarIngreso(actual.getId(), ingreso);

        return respuesta(releer(actual.getId()));
    }

    /**
     * Corrige el monto de un ingreso ya cargado (sección 2.3c). A diferencia
     * de {@code agregarIngreso}, acá SÍ se edita de verdad: probándolo en el
     * teléfono, "anotar el asiento contrario" para un error de tipeo se
     * sintió como vueltas de más -- a diferencia de un {@code Aporte} o una
     * {@code Liquidacion}, donde el rastro de los dos movimientos importa.
     */
    public BalancePersonalRespuesta editarIngreso(String ingresoId, EditarIngresoRequest req) {
        Usuario actual = usuarioActual.requerido();
        boolean existia = usuarios.editarIngreso(actual.getId(), ingresoId, normalizar(req.monto()));
        if (!existia) {
            throw new RecursoNoEncontradoException("No existe el ingreso " + ingresoId);
        }
        return respuesta(releer(actual.getId()));
    }

    /** Saca un ingreso del historial. Mismo criterio que editarIngreso. */
    public BalancePersonalRespuesta borrarIngreso(String ingresoId) {
        Usuario actual = usuarioActual.requerido();
        boolean existia = usuarios.borrarIngreso(actual.getId(), ingresoId);
        if (!existia) {
            throw new RecursoNoEncontradoException("No existe el ingreso " + ingresoId);
        }
        return respuesta(releer(actual.getId()));
    }

    // Se relee en los tres casos: la escritura fue atomica contra la base,
    // no contra el `actual` que ya tenemos en memoria desactualizado.
    private Usuario releer(String usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("El usuario desaparecio"));
    }

    private BalancePersonalRespuesta respuesta(Usuario usuario) {
        BigDecimal ingresado = usuario.totalIngresado();
        BigDecimal gastado = gastos.totalPersonalDe(usuario.getGrupoId(), usuario.getId());

        return new BalancePersonalRespuesta(
                ingresado,
                gastado,
                ingresado.subtract(gastado),
                usuario.getIngresos().stream().map(IngresoRespuesta::desde).toList());
    }

    private BigDecimal normalizar(BigDecimal monto) {
        return monto.setScale(ESCALA_DINERO, REDONDEO);
    }
}
