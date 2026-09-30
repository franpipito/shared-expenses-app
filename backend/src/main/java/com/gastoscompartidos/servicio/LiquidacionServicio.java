package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.LiquidacionRespuesta;
import com.gastoscompartidos.dto.RegistrarLiquidacionRequest;
import com.gastoscompartidos.dto.SaldoTotalRespuesta;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Liquidacion;
import com.gastoscompartidos.modelo.ReferenciaUsuario;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.LiquidacionRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * El saldo de toda la historia ({@code deudas - pagos}) y las liquidaciones
 * que lo mueven. Ver {@link com.gastoscompartidos.modelo.Liquidacion} para el
 * porque de la entidad, y {@link com.gastoscompartidos.dto.SaldoRespuesta}
 * para el saldo del mes, que sigue existiendo sin cambios.
 *
 * Servicio propio y no un agregado mas de ResumenServicio, mismo criterio que
 * separo PozoServicio de GastoServicio: Liquidacion es una entidad con su
 * propia coleccion y su propia operacion de alta, no solo una lectura mas.
 */
@Service
public class LiquidacionServicio {

    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_DINERO);

    private final LiquidacionRepositorio liquidaciones;
    private final GastoRepositorio gastos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    public LiquidacionServicio(LiquidacionRepositorio liquidaciones,
                               GastoRepositorio gastos,
                               UsuarioRepositorio usuarios,
                               UsuarioActual usuarioActual,
                               Clock reloj) {
        this.liquidaciones = liquidaciones;
        this.gastos = gastos;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    public SaldoTotalRespuesta saldoTotal() {
        Usuario actual = usuarioActual.requerido();
        String grupoId = actual.getGrupoId();
        String yo = actual.getId();

        Usuario otro = usuarios.otroIntegranteDe(grupoId, yo);
        if (otro == null) {
            return new SaldoTotalRespuesta(CERO, null, null, null, null, CERO);
        }

        BigDecimal aFavorMio = deudasMenosPagos(grupoId, yo);

        if (aFavorMio.signum() == 0) {
            return new SaldoTotalRespuesta(CERO, null, null, null, null, CERO);
        }
        if (aFavorMio.signum() > 0) {
            return new SaldoTotalRespuesta(aFavorMio,
                    otro.getId(), otro.getNombre(),
                    actual.getId(), actual.getNombre(),
                    aFavorMio);
        }
        return new SaldoTotalRespuesta(aFavorMio.negate(),
                actual.getId(), actual.getNombre(),
                otro.getId(), otro.getNombre(),
                aFavorMio);
    }

    /**
     * Registra "yo le pagué esto a la otra persona" y devuelve el saldo total
     * ya actualizado. Fijate lo que NO se pide: los ids de quien paga y quien
     * recibe -- siempre son quien manda la request y el otro integrante del
     * grupo. Lo que sí puede elegir es la dirección: ver
     * {@link RegistrarLiquidacionRequest#meLoPagaron}.
     */
    public SaldoTotalRespuesta registrar(RegistrarLiquidacionRequest req) {
        Usuario actual = usuarioActual.requerido();

        Usuario otro = usuarios.otroIntegranteDe(actual.getGrupoId(), actual.getId());
        if (otro == null) {
            throw new ReglaDeNegocioException(
                    "Para registrar un pago, la otra persona tiene que estar en tu grupo");
        }

        ReferenciaUsuario de = req.meLoPagaron() ? otro.comoReferencia() : actual.comoReferencia();
        ReferenciaUsuario para = req.meLoPagaron() ? actual.comoReferencia() : otro.comoReferencia();

        Liquidacion liquidacion = new Liquidacion(
                actual.getGrupoId(),
                de,
                para,
                normalizar(req.monto()),
                LocalDate.now(reloj));
        liquidaciones.save(liquidacion);

        return saldoTotal();
    }

    /** El historial completo, mas nueva primero. */
    public List<LiquidacionRespuesta> listar() {
        Usuario actual = usuarioActual.requerido();
        return liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(actual.getGrupoId()).stream()
                .map(LiquidacionRespuesta::desde)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * {@code deudas - pagos}, positivo = a mí me deben.
     *
     * Las liquidaciones se suman en Java y no con un pipeline de Mongo: se
     * esperan pocas (ver LiquidacionRepositorio), a diferencia de los gastos
     * -- de ahi que saldoHistoricoDe sí sea una agregacion.
     */
    private BigDecimal deudasMenosPagos(String grupoId, String yo) {
        BigDecimal deudas = gastos.saldoHistoricoDe(grupoId, yo);

        BigDecimal pagos = liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(grupoId).stream()
                .map(l -> {
                    // Yo pagué: la deuda baja, o sea mi saldo SUBE hacia cero
                    // o positivo. Me pagaron: al reves.
                    if (l.getDe().usuarioId().equals(yo)) return l.getMonto();
                    if (l.getPara().usuarioId().equals(yo)) return l.getMonto().negate();
                    return BigDecimal.ZERO;
                })
                .reduce(CERO, BigDecimal::add);

        return deudas.add(pagos);
    }

    /** El cliente podria mandar 3 decimales. Los llevamos a 2 en el borde. */
    private BigDecimal normalizar(BigDecimal monto) {
        return monto.setScale(ESCALA_DINERO, REDONDEO);
    }
}
