package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.GuardarGastoRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.Pozo;
import com.gastoscompartidos.modelo.ReferenciaCategoria;
import com.gastoscompartidos.modelo.TipoGasto;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * Toda la logica de gastos. El controlador no decide nada.
 *
 * POR QUE YA NO HAY @Transactional EN NINGUN METODO.
 *
 * No es un olvido, y es de las cosas que mas conviene poder explicar de esta
 * migracion.
 *
 * MongoDB si tiene transacciones multi-documento, pero exigen un replica set y
 * un bean MongoTransactionManager que Spring Boot no crea solo. Y sobre todo:
 * aca no comprarian nada. Cada metodo de este servicio escribe **un solo
 * documento**, y en Mongo la escritura de un documento es atomica por si misma.
 * Una transaccion alrededor de una sola escritura es ceremonia.
 *
 * Eso es un cambio de fondo respecto de Postgres, donde `@Transactional` no
 * servia solo para atomicidad: abria la sesion de Hibernate, y de ahi salian el
 * dirty checking y las relaciones lazy. Sin sesion no habia entidades managed.
 * En Mongo no existe nada de eso -— lo que se lee es un objeto comun de Java, y
 * si querés que un cambio se guarde, tenés que llamar a save(). Ni mas ni menos.
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
    private final PozoRepositorio pozos;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    public GastoServicio(GastoRepositorio gastos,
                         CategoriaRepositorio categorias,
                         UsuarioRepositorio usuarios,
                         PozoRepositorio pozos,
                         UsuarioActual usuarioActual,
                         Clock reloj) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.pozos = pozos;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    public GastoRespuesta crear(GuardarGastoRequest req) {
        Usuario actual = usuarioActual.requerido();
        Categoria categoria = buscarCategoria(req.categoriaId());
        Usuario pagador = resolverPagador(req, actual);

        BigDecimal monto = normalizar(req.monto());
        String pozoId = validarPozo(req, actual, null);

        Gasto gasto = new Gasto(
                actual.getGrupoId(),
                // Los snapshots se congelan ACA, al escribir. Ver ReferenciaUsuario.
                pagador.comoReferencia(),
                referencia(categoria),
                monto,
                montoPagadorDe(req, monto),
                req.tipo(),
                req.fecha(),
                req.descripcion().trim(),
                esHormiga(req),
                pozoId,
                req.clienteId());

        return GastoRespuesta.desde(guardarUnaSolaVez(gasto, actual));
    }

    public List<GastoRespuesta> listar(YearMonth mes, String categoriaId, String pagadoPorId) {
        Usuario actual = usuarioActual.requerido();
        // YearMonth.now(reloj) y NO YearMonth.now(): era el unico now() sin reloj
        // de todo el backend. El contenedor corre en UTC, asi que el 30 de
        // septiembre a las 21:30 de Buenos Aires ya es 1 de octubre alla: un
        // GET /gastos sin ?mes= devolvia octubre (vacio) mientras el resumen,
        // que si usa el Clock, devolvia septiembre con sus totales.
        YearMonth periodo = (mes != null) ? mes : YearMonth.now(reloj);

        return gastos.buscarVisibles(
                        actual.getGrupoId(),
                        actual.getId(),
                        periodo.atDay(1),
                        periodo.plusMonths(1).atDay(1),
                        categoriaId,
                        pagadoPorId)
                .stream()
                .map(GastoRespuesta::desde)
                .toList();
    }

    /**
     * Un gasto por id, si lo podes ver.
     *
     * Devuelve 404 tanto si no existe como si es un PERSONAL de la otra
     * persona: la regla de visibilidad ya esta adentro de la consulta, asi que
     * desde aca los dos casos son indistinguibles. Un 403 confirmaria que el
     * gasto existe, que es justo lo que no queremos para los regalos.
     */
    public GastoRespuesta buscar(String id) {
        return GastoRespuesta.desde(buscarVisible(id, usuarioActual.requerido()));
    }

    public GastoRespuesta actualizar(String id, GuardarGastoRequest req) {
        Usuario actual = usuarioActual.requerido();
        Gasto gasto = buscarVisible(id, actual);

        // Bloqueo optimista a nivel de API: el cliente nos dice que version leyo.
        // Si en el medio alguien mas guardo, la version ya no coincide.
        if (req.version() != null && !req.version().equals(gasto.getVersion())) {
            throw new OptimisticLockingFailureException("version desactualizada");
        }

        BigDecimal monto = normalizar(req.monto());

        gasto.setCategoria(referencia(buscarCategoria(req.categoriaId())));
        gasto.setPagadoPor(resolverPagadorAlEditar(req, actual, gasto).comoReferencia());
        gasto.setMonto(monto);
        gasto.setMontoPagador(montoPagadorDe(req, monto));
        gasto.setTipo(req.tipo());
        gasto.setFecha(req.fecha());
        gasto.setDescripcion(req.descripcion().trim());
        gasto.setEsHormiga(esHormiga(req));
        // Se puede mover un gasto adentro o afuera de la vaquita editandolo.
        // Es la valvula de escape para el que se cargo al pozo sin querer, que
        // es el riesgo conocido de que el formulario abra con "Vaquita" puesto
        // durante el viaje.
        gasto.setPozoId(validarPozo(req, actual, gasto.getPozoId()));

        // ACA HABIA UN gastos.flush() Y AHORA HAY UN save(), y el motivo de
        // fondo es el mismo que antes: hay que devolverle al cliente la version
        // NUEVA. Si le devolvieramos la vieja, su proxima edicion daria 409 sin
        // que nadie haya tocado nada.
        //
        // Lo que cambio es de donde sale. Con Hibernate el objeto estaba managed
        // y el UPDATE salia solo por dirty checking; el flush() solo adelantaba
        // el momento. Aca no hay nada parecido: `gasto` es un objeto comun, y si
        // no se llama a save() no se escribe nada. Menos magia, y menos formas
        // de que algo pase sin que lo hayas pedido.
        //
        // save() con @Version puesto hace el update filtrando por la version que
        // tenia, e incrementa. Si otro escribio en el medio, no matchea ningun
        // documento y lanza OptimisticLockingFailureException -- la misma
        // excepcion que lanzaba Hibernate, que es lo que permite que el
        // ManejadorDeErrores no se entere del cambio de base.
        return GastoRespuesta.desde(gastos.save(gasto));
    }

    public void eliminar(String id) {
        Usuario actual = usuarioActual.requerido();
        gastos.delete(buscarVisible(id, actual));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * 404 si no existe Y TAMBIEN si existe pero es privado de la otra persona.
     * La consulta ya aplica la regla de visibilidad, asi que desde aca los dos
     * casos son indistinguibles -- que es exactamente lo que queremos.
     */
    private Gasto buscarVisible(String id, Usuario actual) {
        return gastos.buscarVisiblePorId(id, actual.getGrupoId(), actual.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el gasto " + id));
    }

    private Categoria buscarCategoria(String id) {
        return categorias.findById(id)
                .orElseThrow(() -> new ReglaDeNegocioException("No existe la categoria " + id));
    }

    private static ReferenciaCategoria referencia(Categoria categoria) {
        return new ReferenciaCategoria(categoria.getId(), categoria.getNombre(), categoria.getIcono());
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
        if (!otro.getGrupoId().equals(actual.getGrupoId())) {
            throw new ReglaDeNegocioException("Ese usuario no es de tu grupo");
        }
        return otro;
    }

    /**
     * Quien pago, al EDITAR. No es lo mismo que al crear, y confundirlos costaba
     * dos bugs graves.
     *
     * BUG 1: "pagadoPorId ausente significa lo pague yo" es correcto al CREAR.
     * Al editar significa otra cosa: **reasignar el pagador a quien esta
     * editando**. Franco carga un super de $20.000 al 50/50 (Viole le debe
     * $10.000); Viole abre el gasto -- puede, es COMPARTIDO -- le corrige la
     * descripcion con un PUT sin `pagadoPorId`, que es lo que manda cualquier
     * cliente que siga el contrato documentado del POST, y el saldo pasa a decir
     * que Franco le debe $10.000 a ella. Swing de $20.000, respuesta 200, cero
     * errores.
     *
     * La app mobile se salvaba por casualidad porque reenvia el pagador. O sea
     * que la unica defensa contra invertir el saldo vivia en el cliente, justo
     * al reves de la regla de este proyecto.
     *
     * BUG 2, peor: con `tipo: PERSONAL` y `pagadoPorId` ausente, un gasto
     * COMPARTIDO ajeno se volvia PERSONAL de quien edita. Alcanzable con dos
     * taps: Viole carga "regalo $50.000, compartido"; Franco lo abre, toca el
     * chip Personal y guarda. Desde ese momento `visiblesPara()` lo excluye para
     * ella: no esta en su lista, `GET /gastos/{id}` le da 404, y **no hay forma
     * de recuperarlo**. El gasto que ella cargo desaparecio de su app para
     * siempre.
     *
     * Es exactamente lo que el javadoc de {@link #resolverPagador} dice querer
     * evitar, pero al reves: no creas un gasto privado ajeno, te quedas con el
     * de la otra persona.
     *
     * LAS DOS REGLAS QUE FALTABAN:
     *  1. Sin `pagadoPorId`, se CONSERVA el pagador que ya tenia. Editar no es
     *     apropiarse.
     *  2. Un gasto solo puede volverse PERSONAL si ya era tuyo. Hacer privado
     *     algo ajeno es borrarselo a la otra persona sin avisarle.
     */
    private Usuario resolverPagadorAlEditar(GuardarGastoRequest req, Usuario actual, Gasto gasto) {
        String pagadorActual = gasto.getPagadoPor().usuarioId();
        String pedido = (req.pagadoPorId() != null) ? req.pagadoPorId() : pagadorActual;

        if (req.tipo() == TipoGasto.PERSONAL && !pedido.equals(actual.getId())) {
            throw new ReglaDeNegocioException(
                    "Un gasto personal solo lo puede tener quien lo pago");
        }

        if (pedido.equals(actual.getId())) {
            return actual;
        }

        Usuario otro = usuarios.findById(pedido)
                .orElseThrow(() -> new ReglaDeNegocioException("No existe el usuario " + pedido));
        if (!otro.getGrupoId().equals(actual.getGrupoId())) {
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
     * Guarda el gasto, y si ya estaba, devuelve el que estaba.
     *
     * ES EL PUNTO DONDE LA COLA OFFLINE DEL TELEFONO SE VUELVE SEGURA, y vale
     * entender el escenario completo porque no es obvio.
     *
     * Con una barra de senial, la app manda el POST, el servidor lo escribe, y
     * la respuesta se pierde en el camino de vuelta. El telefono ve un error de
     * red y no tiene ninguna forma de distinguir "no llego" de "llego y no me
     * entere", asi que reintenta. Sin nada que lo frene, ese reintento crea un
     * segundo gasto identico.
     *
     * Y un gasto duplicado no se ve como un error: se ve como un total del mes
     * equivocado, en silencio. Es la peor clase de bug en una app de plata.
     *
     * LA GARANTIA LA DA EL INDICE, NO ESTE CODIGO. El chequeo previo
     * ("existe uno con esta clave?") tendria una ventana de carrera entre el
     * SELECT y el INSERT. Aca se intenta escribir de una y se deja que el indice
     * unico parcial de Gasto rechace el duplicado; recien ahi se busca el que ya
     * estaba. Es el mismo patron que PozoServicio.crear().
     *
     * Sin clienteId no hay indice que viole, asi que un cliente que no la manda
     * (el atajo de iOS, curl) escribe normal y se queda sin la garantia.
     */
    private Gasto guardarUnaSolaVez(Gasto gasto, Usuario actual) {
        try {
            return gastos.save(gasto);
        } catch (DuplicateKeyException e) {
            // Sin clienteId no hay indice de idempotencia que violar, asi que un
            // duplicado aca es otra cosa y no hay que taparlo: buscar por
            // cliente_id null matchearia TODOS los gastos viejos del grupo y
            // devolveria uno cualquiera como si fuera el recien creado.
            if (gasto.getClienteId() == null) throw e;

            return gastos.findByGrupoIdAndClienteId(actual.getGrupoId(), gasto.getClienteId())
                    // Si el indice dijo que hay duplicado, el gasto TIENE que
                    // estar. Si no aparece, algo mas se rompio y no queremos
                    // taparlo devolviendo cualquier cosa.
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Valida el pozo que vino en la request y devuelve su id, o null.
     *
     * DOS REGLAS, y la segunda es de privacidad y no de prolijidad:
     *
     * 1. El pozo tiene que existir, ser de tu grupo y estar ABIERTO. Que el
     *    grupo viaje en la consulta y no en un if es lo mismo que hace
     *    `visiblesPara`: un pozo ajeno simplemente no aparece.
     *
     * 2. **Un gasto del pozo NO puede ser PERSONAL, y se rechaza en vez de
     *    corregirse.** Es tentador "arreglarlo" forzando COMPARTIDO, y seria un
     *    error grave: un gasto PERSONAL es privado de quien lo carga, asi que
     *    promoverlo en silencio publicaria un gasto que su duenio marco como
     *    privado. El caso concreto es el que la usuaria pidio cuidar en la
     *    entrevista: un regalo sorpresa cargado por error con el pozo puesto.
     *    Un 400 es molesto; filtrar el regalo es romper el producto.
     */
    private String validarPozo(GuardarGastoRequest req, Usuario actual, String pozoActual) {
        if (req.pozoId() == null) {
            return null;
        }

        if (req.tipo() == TipoGasto.PERSONAL) {
            throw new ReglaDeNegocioException(
                    "Un gasto personal no puede salir de la vaquita: la vaquita la ven los dos");
        }

        Pozo pozo = pozos.findByIdAndGrupoId(req.pozoId(), actual.getGrupoId())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "No existe la vaquita " + req.pozoId()));

        // UNA VAQUITA CERRADA NO ACEPTA GASTOS NUEVOS, PERO SI CORRECCIONES DE
        // LOS QUE YA TENIA.
        //
        // La diferencia importa de verdad: volviendo del viaje cierran la
        // vaquita, y recien ahi alguien mira la lista y ve que una cena quedo
        // cargada con un cero de mas. Si el cierre congelara tambien las
        // ediciones, ese error seria permanente -- y un registro que no se puede
        // corregir deja de ser confiable.
        //
        // Lo que sigue prohibido es METER un gasto en una vaquita cerrada, que
        // es lo que cambiaria sus numeros despues de darlos por cerrados.
        boolean loEstaMoviendo = !pozo.getId().equals(pozoActual);
        if (pozo.getEstado() != EstadoPozo.ABIERTO && loEstaMoviendo) {
            throw new ReglaDeNegocioException("Esa vaquita ya esta cerrada");
        }

        return pozo.getId();
    }

    /**
     * Cuanto le toca al pagador.
     *
     * Un gasto del pozo es **siempre mitad y mitad**, y el porcentaje que haya
     * mandado el cliente se ignora. No es una simplificacion: la plata del pozo
     * es de los dos desde que entro, asi que no hay reparto que decidir al
     * gastarla. Si aportaron distinto, esa diferencia se salda una sola vez
     * mirando los aportes, no gasto por gasto.
     *
     * En rigor este numero es inerte, porque los gastos del pozo estan excluidos
     * de todos los agregados que miran deuda. Se guarda coherente igual: si
     * alguien saca el gasto del pozo editandolo, los montos que quedan tienen
     * sentido en vez de ser basura heredada.
     */
    private BigDecimal montoPagadorDe(GuardarGastoRequest req, BigDecimal monto) {
        if (req.pozoId() != null) {
            return calcularMontoPagador(monto, TipoGasto.COMPARTIDO, PORCENTAJE_POR_DEFECTO);
        }
        return calcularMontoPagador(monto, req.tipo(), req.porcentajePagador());
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
     *
     * En Mongo esto vale MAS que antes: como el reparto queda resuelto en el
     * documento, las agregaciones del resumen y del saldo son un `$sum` puro. Si
     * guardaramos el porcentaje, habria que multiplicar y redondear adentro del
     * pipeline, donde el redondeo es mucho mas dificil de auditar que aca.
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
