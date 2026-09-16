package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.GuardarGastoRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.Pozo;
import com.gastoscompartidos.modelo.TipoGasto;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las reglas de negocio de los gastos, SIN base de datos.
 *
 * POR QUE EXISTE ESTA CLASE. Hasta aca los tests del proyecto eran todos de
 * clases puras (`Periodo`, `CalculadorDeAnimo`, `Pozo`), y las reglas que mas
 * duelen si se rompen viven en el servicio: el centavo del reparto, quien puede
 * ver un gasto PERSONAL, y que editar no sea apropiarse. Lo unico que las cubria
 * era `scripts/smoke-test.ps1`, que necesita Mongo corriendo y la app levantada
 * -- o sea que no corre en CI y tarda minutos.
 *
 * COMO SE TESTEA UN SERVICIO QUE HABLA CON LA BASE. Con **mocks**: objetos
 * falsos que responden lo que uno les dice. `mock(GastoRepositorio.class)`
 * devuelve un repositorio que no guarda nada; `when(...).thenReturn(...)`
 * programa la respuesta. Asi el test ejercita la LOGICA del servicio sin que
 * exista una base, y corre en milisegundos.
 *
 * Lo que un mock NO prueba: que la consulta de Mongo este bien escrita. Eso lo
 * sigue cubriendo el smoke test, y las dos capas se complementan -- aca las
 * reglas, alla que la base entienda lo que le pedimos.
 */
class GastoServicioTest {

    private static final String GRUPO = "grupo-1";
    private static final String OTRO_GRUPO = "grupo-2";
    private static final String CAT = "cat-cafe";

    private GastoRepositorio gastos;
    private CategoriaRepositorio categorias;
    private UsuarioRepositorio usuarios;
    private PozoRepositorio pozos;
    private UsuarioActual usuarioActual;
    private GastoServicio servicio;

    private Usuario franco;
    private Usuario viole;

    @BeforeEach
    void preparar() {
        gastos = mock(GastoRepositorio.class);
        categorias = mock(CategoriaRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        pozos = mock(PozoRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        // Un reloj fijo: el servicio lo usa para resolver "el mes actual" cuando
        // el listado viene sin ?mes=. Fijarlo evita un test que pase en
        // septiembre y falle en octubre.
        Clock reloj = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new GastoServicio(gastos, categorias, usuarios, pozos, usuarioActual, reloj);

        franco = usuario("u-franco", "Franco", GRUPO);
        viole = usuario("u-viole", "Viole", GRUPO);

        when(usuarioActual.requerido()).thenReturn(franco);
        when(categorias.findById(CAT)).thenReturn(Optional.of(categoria(CAT, "Cafe", "coffee")));
        // save() devuelve lo que le pasaron, que es lo que hace Mongo salvo por
        // el id. Asi el test puede mirar el gasto que el servicio construyo.
        when(gastos.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------- el centavo

    @Nested
    @DisplayName("El centavo del reparto")
    class Centavo {

        @Test
        @DisplayName("$10.01 al 50/50: el pagador absorbe el centavo")
        void centavoImpar() {
            GastoRespuesta r = servicio.crear(compartido("10.01", 50));

            // 10.01 * 50 / 100 = 5.005, que redondea a 5.01 HACIA ARRIBA.
            assertThat(r.montoPagador()).isEqualByComparingTo("5.01");
            // Y la deuda es la resta, nunca otra division: por eso las dos
            // partes suman el total exacto y no aparece ni desaparece un centavo.
            assertThat(r.deudaGenerada()).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("las dos partes suman siempre el monto, sin importar el porcentaje")
        void sinFugas() {
            for (int pct : new int[] {0, 33, 50, 67, 70, 100}) {
                GastoRespuesta r = servicio.crear(compartido("10.01", pct));
                assertThat(r.montoPagador().add(r.deudaGenerada()))
                        .as("porcentaje %d", pct)
                        .isEqualByComparingTo("10.01");
            }
        }

        @Test
        @DisplayName("un PERSONAL no genera deuda: le toca todo a quien lo pago")
        void personalNoGeneraDeuda() {
            GastoRespuesta r = servicio.crear(personal("4000.00"));

            assertThat(r.montoPagador()).isEqualByComparingTo("4000.00");
            assertThat(r.deudaGenerada()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("sin porcentaje, el default es 50")
        void defaultCincuenta() {
            GastoRespuesta r = servicio.crear(compartido("100.00", null));
            assertThat(r.montoPagador()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("un monto con tres decimales se normaliza a dos en el borde")
        void tresDecimales() {
            GastoRespuesta r = servicio.crear(personal("10.005"));
            assertThat(r.monto()).isEqualByComparingTo("10.01");
        }
    }

    // ------------------------------------------------------- quien pago (alta)

    @Nested
    @DisplayName("Quien pago, al crear")
    class PagadorAlCrear {

        @Test
        @DisplayName("sin pagadoPorId paga quien carga")
        void porDefectoPagoYo() {
            GastoRespuesta r = servicio.crear(compartido("100.00", 50));
            assertThat(r.pagadoPor().id()).isEqualTo(franco.getId());
        }

        @Test
        @DisplayName("un COMPARTIDO puede ir a nombre del otro integrante")
        void compartidoANombreDelOtro() {
            when(usuarios.findById(viole.getId())).thenReturn(Optional.of(viole));

            GastoRespuesta r = servicio.crear(
                    pedido("100.00", TipoGasto.COMPARTIDO, 50, viole.getId(), null));

            assertThat(r.pagadoPor().id()).isEqualTo(viole.getId());
        }

        @Test
        @DisplayName("un PERSONAL a nombre de otro se rechaza: seria un gasto privado ajeno")
        void personalANombreDeOtro() {
            assertThatThrownBy(() -> servicio.crear(
                    pedido("100.00", TipoGasto.PERSONAL, null, viole.getId(), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("solo lo puede cargar quien lo pago");
        }

        @Test
        @DisplayName("no se puede cargar a nombre de alguien de otro grupo")
        void pagadorDeOtroGrupo() {
            Usuario ajeno = usuario("u-ajeno", "Ajeno", OTRO_GRUPO);
            when(usuarios.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

            assertThatThrownBy(() -> servicio.crear(
                    pedido("100.00", TipoGasto.COMPARTIDO, 50, ajeno.getId(), null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no es de tu grupo");
        }
    }

    // ---------------------------------------------------- quien pago (edicion)

    @Nested
    @DisplayName("Quien pago, al editar: editar no es apropiarse")
    class PagadorAlEditar {

        @Test
        @DisplayName("sin pagadoPorId se CONSERVA el pagador, no pasa a quien edita")
        void conservaElPagador() {
            // El bug que esto cubre invertia el saldo entero: Viole abria un
            // gasto de Franco -- puede, es COMPARTIDO -- le corregia la
            // descripcion con un PUT sin pagadoPorId, y la deuda cambiaba de
            // direccion. Respuesta 200, cero errores.
            Gasto deFranco = gastoExistente(franco, TipoGasto.COMPARTIDO, "20000.00", null);
            when(usuarioActual.requerido()).thenReturn(viole);
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(deFranco));
            // El servicio busca al pagador conservado para devolver el Usuario
            // entero. Que haga falta stubbearlo ya dice que NO se lo apropio.
            when(usuarios.findById(franco.getId())).thenReturn(Optional.of(franco));

            GastoRespuesta r = servicio.actualizar("g-1",
                    pedido("20000.00", TipoGasto.COMPARTIDO, 50, null, null));

            assertThat(r.pagadoPor().id())
                    .as("el pagador tiene que seguir siendo Franco")
                    .isEqualTo(franco.getId());
        }

        @Test
        @DisplayName("no se puede volver PERSONAL un gasto ajeno: seria borrarselo")
        void noSeApropiaVolviendoloPersonal() {
            // Con tipo PERSONAL y pagadoPorId ausente, un COMPARTIDO de Viole
            // se volvia PERSONAL de Franco. Desde ese momento ella no lo veia
            // mas en ningun lado, y no habia forma de recuperarlo.
            Gasto deViole = gastoExistente(viole, TipoGasto.COMPARTIDO, "50000.00", null);
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(deViole));

            assertThatThrownBy(() -> servicio.actualizar("g-1",
                    pedido("50000.00", TipoGasto.PERSONAL, null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("solo lo puede tener quien lo pago");
        }

        @Test
        @DisplayName("volver PERSONAL un gasto propio si se puede")
        void elPropioSiSePuedeVolverPersonal() {
            Gasto deFranco = gastoExistente(franco, TipoGasto.COMPARTIDO, "3000.00", null);
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(deFranco));

            GastoRespuesta r = servicio.actualizar("g-1",
                    pedido("3000.00", TipoGasto.PERSONAL, null, null, null));

            assertThat(r.tipo()).isEqualTo(TipoGasto.PERSONAL);
            assertThat(r.deudaGenerada()).isEqualByComparingTo("0.00");
        }
    }

    // ----------------------------------------------------------- visibilidad

    @Nested
    @DisplayName("Visibilidad")
    class Visibilidad {

        @Test
        @DisplayName("un gasto que la consulta no devuelve da 404, no 403")
        void noVisibleDa404() {
            // La regla de visibilidad vive en la consulta, asi que desde el
            // servicio "no existe" y "es privado de la otra persona" son
            // indistinguibles. Es a proposito: un 403 confirmaria que existe, y
            // el caso que hay que cuidar es el regalo sorpresa.
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.porId("g-ajeno"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("borrar algo que no ves tampoco se puede, y no borra nada")
        void borrarLoQueNoVes() {
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.eliminar("g-ajeno"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verify(gastos, never()).delete(any());
        }
    }

    // ---------------------------------------------------------- idempotencia

    @Nested
    @DisplayName("La clave de idempotencia de la cola offline")
    class Idempotencia {

        @Test
        @DisplayName("si el indice rechaza el duplicado, devuelve el gasto que ya estaba")
        void reintentoDevuelveElMismo() {
            // El escenario: el POST llego, el servidor lo escribio, y la
            // respuesta se perdio. El telefono no puede distinguir eso de "no
            // llego", asi que reintenta -- y sin esto crearia un segundo gasto.
            Gasto yaEstaba = gastoExistente(franco, TipoGasto.PERSONAL, "500.00", null);
            when(gastos.save(any(Gasto.class))).thenThrow(new DuplicateKeyException("indice unico"));
            when(gastos.findByGrupoIdAndClienteId(GRUPO, "clave-1"))
                    .thenReturn(Optional.of(yaEstaba));

            GastoRespuesta r = servicio.crear(
                    pedido("500.00", TipoGasto.PERSONAL, null, null, "clave-1"));

            assertThat(r.monto()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("sin clienteId, un duplicado NO se tapa: se propaga")
        void sinClaveNoSeTapa() {
            // Sin clienteId no hay indice de idempotencia que violar, asi que un
            // duplicado aca significa otra cosa. Buscar por cliente_id null
            // matchearia todos los gastos viejos del grupo y devolveria uno
            // cualquiera como si fuera el recien creado.
            when(gastos.save(any(Gasto.class))).thenThrow(new DuplicateKeyException("otra cosa"));

            assertThatThrownBy(() -> servicio.crear(personal("500.00")))
                    .isInstanceOf(DuplicateKeyException.class);
            verify(gastos, never()).findByGrupoIdAndClienteId(anyString(), any());
        }
    }

    // --------------------------------------------------------------- vaquita

    @Nested
    @DisplayName("La vaquita")
    class Vaquita {

        @Test
        @DisplayName("un gasto de la vaquita es mitad y mitad, ignorando el porcentaje pedido")
        void siempreMitadYMitad() {
            when(pozos.findByIdAndGrupoId("pozo-1", GRUPO))
                    .thenReturn(Optional.of(pozo("pozo-1", EstadoPozo.ABIERTO)));

            GastoRespuesta r = servicio.crear(
                    conPozo("100.00", 90, "pozo-1"));

            // La plata del pozo es de los dos desde que entro: no hay reparto
            // que decidir al gastarla.
            assertThat(r.montoPagador()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("un PERSONAL no puede salir de la vaquita, y se rechaza en vez de corregirse")
        void personalNoSaleDeLaVaquita() {
            // Forzar COMPARTIDO en silencio publicaria un gasto que su duenio
            // marco como privado: el regalo sorpresa que la usuaria pidio
            // cuidar. Un 400 es molesto; filtrar el regalo rompe el producto.
            GuardarGastoRequest req = pedidoConPozo("100.00", TipoGasto.PERSONAL, "pozo-1", null);

            assertThatThrownBy(() -> servicio.crear(req))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("la vaquita la ven los dos");
        }

        @Test
        @DisplayName("una vaquita cerrada no acepta gastos nuevos")
        void cerradaNoAceptaNuevos() {
            when(pozos.findByIdAndGrupoId("pozo-1", GRUPO))
                    .thenReturn(Optional.of(pozo("pozo-1", EstadoPozo.CERRADO)));

            assertThatThrownBy(() -> servicio.crear(conPozo("100.00", 50, "pozo-1")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya esta cerrada");
        }

        @Test
        @DisplayName("pero SI deja corregir los que ya tenia")
        void cerradaSiDejaCorregir() {
            // Volviendo del viaje cierran la vaquita, y recien ahi alguien ve
            // que una cena quedo cargada con un cero de mas. Si el cierre
            // congelara las ediciones, ese error seria permanente.
            when(pozos.findByIdAndGrupoId("pozo-1", GRUPO))
                    .thenReturn(Optional.of(pozo("pozo-1", EstadoPozo.CERRADO)));
            Gasto delViaje = gastoExistente(franco, TipoGasto.COMPARTIDO, "80000.00", "pozo-1");
            when(gastos.buscarVisiblePorId(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(delViaje));

            GastoRespuesta r = servicio.actualizar("g-1", conPozo("8000.00", 50, "pozo-1"));

            assertThat(r.monto()).isEqualByComparingTo("8000.00");
            assertThat(r.pozoId()).isEqualTo("pozo-1");
        }

        @Test
        @DisplayName("una vaquita de otro grupo no existe")
        void pozoAjeno() {
            when(pozos.findByIdAndGrupoId("pozo-ajeno", GRUPO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(conPozo("100.00", 50, "pozo-ajeno")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("No existe la vaquita");
        }
    }

    // ------------------------------------------------------------- utilidades

    private GuardarGastoRequest personal(String monto) {
        return pedido(monto, TipoGasto.PERSONAL, null, null, null);
    }

    private GuardarGastoRequest compartido(String monto, Integer pct) {
        return pedido(monto, TipoGasto.COMPARTIDO, pct, null, null);
    }

    private GuardarGastoRequest conPozo(String monto, Integer pct, String pozoId) {
        return new GuardarGastoRequest(new BigDecimal(monto), CAT, LocalDate.of(2026, 9, 6),
                "cena", TipoGasto.COMPARTIDO, pct, null, false, null, pozoId, null);
    }

    private GuardarGastoRequest pedidoConPozo(String monto, TipoGasto tipo, String pozoId, Integer pct) {
        return new GuardarGastoRequest(new BigDecimal(monto), CAT, LocalDate.of(2026, 9, 6),
                "cena", tipo, pct, null, false, null, pozoId, null);
    }

    private GuardarGastoRequest pedido(String monto, TipoGasto tipo, Integer pct,
                                       String pagadoPorId, String clienteId) {
        return new GuardarGastoRequest(new BigDecimal(monto), CAT, LocalDate.of(2026, 9, 6),
                "cafe de la manana", tipo, pct, pagadoPorId, false, null, null, clienteId);
    }

    private Gasto gastoExistente(Usuario pagador, TipoGasto tipo, String monto, String pozoId) {
        BigDecimal m = new BigDecimal(monto);
        Gasto g = new Gasto(GRUPO, pagador.comoReferencia(),
                new com.gastoscompartidos.modelo.ReferenciaCategoria(CAT, "Cafe", "coffee"),
                m, tipo == TipoGasto.PERSONAL ? m : m.divide(new BigDecimal("2")),
                tipo, LocalDate.of(2026, 9, 6), "lo que sea", false, pozoId, null);
        fijarId(g, "g-1");
        return g;
    }

    private Usuario usuario(String id, String nombre, String grupoId) {
        Usuario u = new Usuario(nombre, nombre.toLowerCase() + "@local", "hash", grupoId);
        fijarId(u, id);
        return u;
    }

    private Categoria categoria(String id, String nombre, String icono) {
        Categoria c = new Categoria(nombre, icono);
        fijarId(c, id);
        return c;
    }

    private Pozo pozo(String id, EstadoPozo estado) {
        Pozo p = new Pozo(GRUPO, "Bariloche", new BigDecimal("800000.00"), null, null);
        fijarId(p, id);
        if (estado != EstadoPozo.ABIERTO) {
            escribirCampo(p, "estado", estado);
        }
        return p;
    }

    /**
     * El `id` lo pone Mongo al guardar, asi que las entidades recien construidas
     * lo tienen en null y no hay setter -- a proposito: nadie deberia poder
     * cambiarle el id a un documento. En un test hace falta igual, porque las
     * reglas comparan ids. Se escribe por reflexion, que es la forma de decir
     * "esto es una licencia del test y no una puerta que abrimos en el modelo".
     */
    private static void fijarId(Object entidad, String id) {
        escribirCampo(entidad, "id", id);
    }

    private static void escribirCampo(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "No se pudo escribir " + campo + " en " + objeto.getClass().getSimpleName(), e);
        }
    }
}
