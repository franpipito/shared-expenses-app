package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.ResumenRespuesta;
import com.gastoscompartidos.dto.SaldoRespuesta;
import com.gastoscompartidos.dto.TotalPorCategoria;
import com.gastoscompartidos.modelo.AnimoNutria;
import com.gastoscompartidos.modelo.ReferenciaCategoria;
import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.Periodo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Los dos agregados: el resumen personal del mes y el saldo de la pareja.
 *
 * Ninguno de los dos se guarda en ningun lado. Se calculan cada vez, y por eso
 * es imposible que queden desincronizados con los gastos.
 */
@Service
public class ResumenServicio {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final GastoRepositorio gastos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    /**
     * El Clock se inyecta en vez de usar LocalDate.now() directo. Cuesta una
     * linea y hace que "hoy" sea un dato y no una constante del universo, con lo
     * cual un test puede fijar la fecha en vez de rezar para no correr a las
     * 23:59 del ultimo dia del mes.
     *
     * Spring Boot no define un Clock por defecto, asi que lo declaramos nosotros
     * en la clase de arranque.
     */
    public ResumenServicio(GastoRepositorio gastos,
                           UsuarioRepositorio usuarios,
                           UsuarioActual usuarioActual,
                           Clock reloj) {
        this.gastos = gastos;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    public ResumenRespuesta resumen(YearMonth mes) {
        Usuario actual = usuarioActual.requerido();
        String grupoId = actual.getGrupoId();
        String yo = actual.getId();

        YearMonth periodoPedido = (mes != null) ? mes : YearMonth.now(reloj);
        Periodo actualP = Periodo.transcurridoDe(periodoPedido, LocalDate.now(reloj));
        Periodo anteriorP = actualP.mismoTramoDelMesAnterior();

        // Traemos los documentos del mes y agrupamos en memoria. A esta escala
        // (dos personas, decenas de gastos por mes) el costo es despreciable, y
        // a cambio la logica de agregacion queda en Java: legible y testeable
        // sin base. Si algun dia un mes trajera miles de documentos, esto se
        // mueve a un $group en un pipeline, como ya estan sumarHormigaDe y
        // saldoDe.
        //
        // Con los snapshots embebidos esto se volvio mas barato que en Postgres:
        // agrupar por categoria no necesita ir a buscar ninguna categoria, su
        // nombre y su icono ya vienen adentro de cada gasto.
        List<Gasto> delMes = gastos.buscarVisibles(
                grupoId, yo, actualP.desde(), actualP.hasta(), null, null);

        List<TotalPorCategoria> porCategoria = agruparPorCategoria(delMes, yo);

        BigDecimal total = sumar(delMes.stream().map(g -> g.parteDe(yo)).toList());
        BigDecimal totalHormiga = sumar(delMes.stream()
                .filter(Gasto::esHormiga)
                .map(g -> g.parteDe(yo))
                .toList());

        BigDecimal hormigaAnterior = gastos.sumarHormigaDe(
                grupoId, yo, anteriorP.desde(), anteriorP.hasta());
        boolean hayDatosAnteriores = gastos.contarEn(
                grupoId, yo, anteriorP.desde(), anteriorP.hasta()) > 0;

        AnimoNutria animo = CalculadorDeAnimo.calcular(
                totalHormiga, hormigaAnterior, hayDatosAnteriores);

        return new ResumenRespuesta(
                periodoPedido.toString(),
                actualP.desde(),
                actualP.hasta(),
                total,
                totalHormiga,
                hormigaAnterior,
                porCategoria,
                animo);
    }

    public SaldoRespuesta saldo(YearMonth mes) {
        Usuario actual = usuarioActual.requerido();
        String grupoId = actual.getGrupoId();
        String yo = actual.getId();

        YearMonth periodoPedido = (mes != null) ? mes : YearMonth.now(reloj);
        Periodo periodo = Periodo.transcurridoDe(periodoPedido, LocalDate.now(reloj));

        // Positivo: me deben. Negativo: debo.
        BigDecimal aFavorMio = gastos.saldoDe(
                grupoId, yo, periodo.desde(), periodo.hasta());

        Usuario otro = otroIntegrante(grupoId, yo);

        if (aFavorMio.signum() == 0 || otro == null) {
            return new SaldoRespuesta(periodoPedido.toString(), CERO,
                    null, null, null, null, CERO);
        }
        if (aFavorMio.signum() > 0) {
            return new SaldoRespuesta(periodoPedido.toString(), aFavorMio,
                    otro.getId(), otro.getNombre(),
                    actual.getId(), actual.getNombre(),
                    aFavorMio);
        }
        return new SaldoRespuesta(periodoPedido.toString(), aFavorMio.negate(),
                actual.getId(), actual.getNombre(),
                otro.getId(), otro.getNombre(),
                aFavorMio);
    }

    // ---------------------------------------------------------------- helpers

    private List<TotalPorCategoria> agruparPorCategoria(List<Gasto> delMes, String yo) {
        Map<String, List<Gasto>> agrupados = delMes.stream()
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria().categoriaId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return agrupados.values().stream()
                .map(deLaCategoria -> {
                    // El snapshot embebido en el gasto, no la categoria de su
                    // coleccion: no hace falta ir a buscarla.
                    ReferenciaCategoria categoria = deLaCategoria.get(0).getCategoria();
                    return new TotalPorCategoria(
                            categoria.categoriaId(),
                            categoria.nombre(),
                            categoria.icono(),
                            sumar(deLaCategoria.stream().map(g -> g.parteDe(yo)).toList()),
                            sumar(deLaCategoria.stream()
                                    .filter(Gasto::esHormiga)
                                    .map(g -> g.parteDe(yo))
                                    .toList()));
                })
                .sorted(Comparator.comparing(TotalPorCategoria::total).reversed())
                .toList();
    }

    /** Arranca en CERO con escala 2 para que un mes vacio devuelva 0.00 y no 0. */
    private BigDecimal sumar(List<BigDecimal> montos) {
        return montos.stream().reduce(CERO, BigDecimal::add);
    }

    private Usuario otroIntegrante(String grupoId, String yo) {
        return usuarios.findByGrupoIdOrderByIdAsc(grupoId).stream()
                .filter(u -> !u.getId().equals(yo))
                .findFirst()
                .orElse(null);
    }
}
