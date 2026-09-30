package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.BalancePersonalRespuesta;
import com.gastoscompartidos.dto.IngresoRespuesta;
import com.gastoscompartidos.dto.RegistrarIngresoRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
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

        // Cero no es un ingreso ni una correccion: es ruido en el historial.
        // El negativo si vale, y es como se deshace un ingreso equivocado --
        // mismo criterio que PozoServicio.aportar() con los aportes.
        BigDecimal monto = normalizar(req.monto());
        if (monto.signum() == 0) {
            throw new ReglaDeNegocioException("El monto no puede ser cero");
        }

        Ingreso ingreso = new Ingreso(monto, LocalDate.now(reloj));
        usuarios.agregarIngreso(actual.getId(), ingreso);

        // Se relee: el $push fue atomico contra la base, no contra el
        // `actual` que ya tenemos en memoria desactualizado.
        Usuario actualizado = usuarios.findById(actual.getId())
                .orElseThrow(() -> new IllegalStateException("El usuario desaparecio durante el alta"));
        return respuesta(actualizado);
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
