package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.GuardarGastoRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.TipoGasto;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Toda la logica de gastos. El controlador no decide nada.
 */
@Service
public class GastoServicio {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int PORCENTAJE_POR_DEFECTO = 50;
    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private final GastoRepositorio gastos;
    private final CategoriaRepositorio categorias;
    private final UsuarioRepositorio usuarios;
    private final UsuarioActual usuarioActual;

    public GastoServicio(GastoRepositorio gastos,
                         CategoriaRepositorio categorias,
                         UsuarioRepositorio usuarios,
                         UsuarioActual usuarioActual) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
    }

    @Transactional
    public GastoRespuesta crear(GuardarGastoRequest req) {
        Usuario actual = usuarioActual.requerido();
        Categoria categoria = buscarCategoria(req.categoriaId());
        Usuario pagador = resolverPagador(req, actual);

        BigDecimal monto = normalizar(req.monto());

        Gasto gasto = new Gasto(
                actual.getGrupo(),
                pagador,
                categoria,
                monto,
                calcularMontoPagador(monto, req.tipo(), req.porcentajePagador()),
                req.tipo(),
                req.fecha(),
                req.descripcion().trim(),
                esHormiga(req));

        return GastoRespuesta.desde(gastos.save(gasto));
    }

    @Transactional(readOnly = true)
    public List<GastoRespuesta> listar(YearMonth mes, Long categoriaId, Long pagadoPorId) {
        Usuario actual = usuarioActual.requerido();
        YearMonth periodo = (mes != null) ? mes : YearMonth.now();

        return gastos.buscarVisibles(
                        actual.getGrupo().getId(),
                        actual.getId(),
                        periodo.atDay(1),
                        periodo.plusMonths(1).atDay(1),
                        categoriaId,
                        pagadoPorId)
                .stream()
                .map(GastoRespuesta::desde)
                .toList();
    }

    @Transactional
    public GastoRespuesta actualizar(Long id, GuardarGastoRequest req) {
        Usuario actual = usuarioActual.requerido();
        Gasto gasto = buscarVisible(id, actual);

        // Bloqueo optimista a nivel de API: el cliente nos dice que version leyo.
        // Si en el medio alguien mas guardo, la version ya no coincide.
        if (req.version() != null && !req.version().equals(gasto.getVersion())) {
            throw new OptimisticLockingFailureException("version desactualizada");
        }

        BigDecimal monto = normalizar(req.monto());

        gasto.setCategoria(buscarCategoria(req.categoriaId()));
        gasto.setPagadoPor(resolverPagador(req, actual));
        gasto.setMonto(monto);
        gasto.setMontoPagador(calcularMontoPagador(monto, req.tipo(), req.porcentajePagador()));
        gasto.setTipo(req.tipo());
        gasto.setFecha(req.fecha());
        gasto.setDescripcion(req.descripcion().trim());
        gasto.setEsHormiga(esHormiga(req));

        // No hay save(). El gasto esta MANAGED, asi que Hibernate compara contra
        // la foto que guardo al cargarlo y emite el UPDATE. Eso es dirty checking.
        //
        // Pero el flush() SI hace falta, y es sutil: sin el, Hibernate recien
        // escribiria al cerrar la transaccion, DESPUES de que armemos el DTO.
        // Y como es en el UPDATE donde incrementa `version`, le devolveriamos al
        // cliente la version vieja -- con lo cual su proxima edicion daria 409
        // sin que nadie haya tocado nada.
        gastos.flush();

        return GastoRespuesta.desde(gasto);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario actual = usuarioActual.requerido();
        gastos.delete(buscarVisible(id, actual));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * 404 si no existe Y TAMBIEN si existe pero es privado de la otra persona.
     * La consulta ya aplica la regla de visibilidad, asi que desde aca los dos
     * casos son indistinguibles -- que es exactamente lo que queremos.
     */
    private Gasto buscarVisible(Long id, Usuario actual) {
        return gastos.buscarVisiblePorId(id, actual.getGrupo().getId(), actual.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el gasto " + id));
    }

    private Categoria buscarCategoria(Long id) {
        return categorias.findById(id)
                .orElseThrow(() -> new ReglaDeNegocioException("No existe la categoria " + id));
    }

    /**
     * Por defecto paga quien carga el gasto. Se puede indicar al otro integrante
     * ("pagaste vos el super"), pero solo en gastos COMPARTIDO: cargar un gasto
     * PERSONAL a nombre de otro crearia un gasto privado ajeno, que ademas
     * dejarias de ver en cuanto lo guardas.
     */
    private Usuario resolverPagador(GuardarGastoRequest req, Usuario actual) {
        if (req.pagadoPorId() == null || req.pagadoPorId().equals(actual.getId())) {
            return actual;
        }
        if (req.tipo() == TipoGasto.PERSONAL) {
            throw new ReglaDeNegocioException("Un gasto personal solo lo puede cargar quien lo pago");
        }
        Usuario otro = usuarios.findById(req.pagadoPorId())
                .orElseThrow(() -> new ReglaDeNegocioException("No existe el usuario " + req.pagadoPorId()));
        if (!otro.getGrupo().getId().equals(actual.getGrupo().getId())) {
            throw new ReglaDeNegocioException("Ese usuario no es de tu grupo");
        }
        return otro;
    }

    /** Ausente o null significa "no es hormiga". */
    private boolean esHormiga(GuardarGastoRequest req) {
        return Boolean.TRUE.equals(req.esHormiga());
    }

    /** El cliente podria mandar 3 decimales. Los llevamos a 2 en el borde. */
    private BigDecimal normalizar(BigDecimal monto) {
        return monto.setScale(ESCALA_DINERO, REDONDEO);
    }

    /**
     * LA REGLA DEL CENTAVO, en el unico lugar de todo el sistema donde vive.
     *
     * Un gasto PERSONAL tiene montoPagador == monto, asi que su deuda generada
     * (monto - montoPagador) da cero y no ensucia el saldo.
     *
     * En un COMPARTIDO dividimos UNA sola vez y redondeamos aca; la parte del
     * otro sale por resta en `Gasto.deudaGenerada()`. Por eso las dos partes
     * suman siempre el total exacto: si dividieramos las dos por separado,
     * $10,01 al 50/50 podria dar 5,01 y 5,01 = $10,02.
     *
     * Con HALF_UP, en un empate el centavo se lo queda quien pago (5,005 -> 5,01
     * para el pagador, 5,00 de deuda para el otro), que ademas es lo mas justo:
     * el que puso la plata absorbe la diferencia.
     */
    private BigDecimal calcularMontoPagador(BigDecimal monto, TipoGasto tipo, Integer porcentaje) {
        if (tipo == TipoGasto.PERSONAL) {
            return monto;
        }
        int pct = (porcentaje != null) ? porcentaje : PORCENTAJE_POR_DEFECTO;
        return monto.multiply(BigDecimal.valueOf(pct))
                .divide(CIEN, ESCALA_DINERO, REDONDEO);
    }
}
