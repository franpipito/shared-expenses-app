package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.ResumenRespuesta;
import com.gastoscompartidos.dto.SaldoRespuesta;
import com.gastoscompartidos.modelo.AnimoNutria;
import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.ReferenciaCategoria;
import com.gastoscompartidos.modelo.TipoGasto;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de los agregados: el resumen personal y el saldo de la pareja.
 *
 * QUE SE PRUEBA ACA Y QUE NO, porque la division no es obvia.
 *
 * `ResumenServicio` reparte el trabajo entre Mongo y Java. Lo que suma **la
 * base** -- `sumarHormigaDe`, `saldoDe`, `contarEn` -- son mocks, asi que estos
 * tests NO prueban esas consultas: un mock devuelve lo que se le dijo aunque el
 * `$match` este al reves. Eso lo cubre el smoke test contra una base real.
 *
 * Lo que si se prueba es todo lo que el servicio hace **en Java**: sumar
 * `parteDe(yo)` gasto por gasto, agrupar por categoria con el snapshot embebido,
 * ordenar de mayor a menor, y decidir la direccion de la deuda. Ahi vive la
 * logica que no se ve en ninguna query.
 *
 * Y lo mismo vale para el filtro `sinPozo()`: **no se prueba aca**, porque vive
 * en el `Criteria` de `GastoConsultasImpl` y no en este servicio. Desde este
 * lado, un gasto de la vaquita simplemente no llega en la lista.
 */
class ResumenServicioTest {

    private static final String GRUPO = "grupo-1";
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private GastoRepositorio gastos;
    private UsuarioRepositorio usuarios;
    private UsuarioActual usuarioActual;
    private ResumenServicio servicio;

    private Usuario franco;
    private Usuario viole;

    @BeforeEach
    void preparar() {
        gastos = mock(GastoRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        // 6 de septiembre: el mes esta a mitad de camino, que es el caso normal
        // y ademas pasa el minimo de 5 dias que pide CalculadorDeAnimo.
        Clock reloj = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new ResumenServicio(gastos, usuarios, usuarioActual, reloj);

        franco = usuario("u-franco", "Franco");
        viole = usuario("u-viole", "Viole");

        when(usuarioActual.requerido()).thenReturn(franco);
        when(usuarios.findByGrupoIdOrderByIdAsc(GRUPO)).thenReturn(List.of(franco, viole));
        when(gastos.sumarHormigaDe(anyString(), anyString(), any(), any())).thenReturn(CERO);
        when(gastos.contarEn(anyString(), anyString(), any(), any())).thenReturn(0L);
        when(gastos.saldoDe(anyString(), anyString(), any(), any())).thenReturn(CERO);
    }

    @Nested
    @DisplayName("El resumen personal")
    class Resumen {

        @Test
        @DisplayName("el total es MI parte, no el total del grupo")
        void miParteYNoElTotal() {
            // Es la diferencia que define la pantalla: un compartido de $1.000
            // al 50/50 suma $500 a mi resumen, no $1.000.
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            gasto(franco, "1000.00", "500.00", TipoGasto.COMPARTIDO, false, "Cafe"),
                            gasto(franco, "300.00", "300.00", TipoGasto.PERSONAL, false, "Cafe")));

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            assertThat(r.total()).isEqualByComparingTo("800.00");
        }

        @Test
        @DisplayName("de un gasto que pago la otra persona, mi parte es lo que le debo")
        void loQueLeDebo() {
            // parteDe() cambia de lado segun quien pago: si pago Viole, lo mio
            // es la deuda que genero, no el montoPagador.
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            gasto(viole, "1000.00", "700.00", TipoGasto.COMPARTIDO, false, "Cafe")));

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            assertThat(r.total()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("el total hormiga suma solo los marcados, con mi parte de cada uno")
        void totalHormiga() {
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            gasto(franco, "4000.00", "4000.00", TipoGasto.PERSONAL, true, "Uber"),
                            gasto(franco, "4000.00", "4000.00", TipoGasto.PERSONAL, false, "Uber"),
                            gasto(franco, "1000.00", "500.00", TipoGasto.COMPARTIDO, true, "Cafe")));

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            // Los dos ubers de $4.000, uno marcado y el otro no: es el ejemplo
            // del mockup, y es literalmente el producto.
            assertThat(r.total()).isEqualByComparingTo("8500.00");
            assertThat(r.totalHormiga()).isEqualByComparingTo("4500.00");
        }

        @Test
        @DisplayName("el desglose agrupa por categoria y ordena de mayor a menor")
        void desglosePorCategoria() {
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            gasto(franco, "500.00", "500.00", TipoGasto.PERSONAL, false, "Cafe"),
                            gasto(franco, "9000.00", "9000.00", TipoGasto.PERSONAL, true, "Uber"),
                            gasto(franco, "500.00", "500.00", TipoGasto.PERSONAL, false, "Cafe")));

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            assertThat(r.porCategoria()).hasSize(2);
            // Uber primero aunque en la lista viniera segundo.
            assertThat(r.porCategoria().get(0).nombre()).isEqualTo("Uber");
            assertThat(r.porCategoria().get(0).total()).isEqualByComparingTo("9000.00");
            assertThat(r.porCategoria().get(1).nombre()).isEqualTo("Cafe");
            assertThat(r.porCategoria().get(1).total()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("sin gastos, todo en cero y la nutria no juzga")
        void mesVacio() {
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            assertThat(r.total()).isEqualByComparingTo("0.00");
            assertThat(r.totalHormiga()).isEqualByComparingTo("0.00");
            assertThat(r.porCategoria()).isEmpty();
            // Cero hormiga es CONTENTA aunque no haya historia con que comparar.
            assertThat(r.animo()).isEqualTo(AnimoNutria.CONTENTA);
        }

        @Test
        @DisplayName("el periodo que devuelve es el tramo TRANSCURRIDO, no el mes entero")
        void tramoTranscurrido() {
            when(gastos.buscarVisibles(anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            ResumenRespuesta r = servicio.resumen(YearMonth.of(2026, 9));

            // Hoy es el 6, asi que el rango es [1, 7): seis dias, no treinta.
            // Es lo que hace justa la comparacion de la nutria.
            assertThat(r.desde()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(r.hasta()).isEqualTo(LocalDate.of(2026, 9, 7));
        }
    }

    @Nested
    @DisplayName("El saldo de la pareja")
    class Saldo {

        @Test
        @DisplayName("positivo: me deben, y el deudor es la otra persona")
        void meDeben() {
            when(gastos.saldoDe(anyString(), anyString(), any(), any()))
                    .thenReturn(new BigDecimal("505.00"));

            SaldoRespuesta r = servicio.saldo(YearMonth.of(2026, 9));

            assertThat(r.monto()).isEqualByComparingTo("505.00");
            assertThat(r.deudorNombre()).isEqualTo("Viole");
            assertThat(r.acreedorNombre()).isEqualTo("Franco");
        }

        @Test
        @DisplayName("negativo: debo yo, y el monto se muestra en positivo")
        void debo() {
            // El signo dice la direccion; el numero que se muestra es siempre
            // positivo, porque la pantalla ya dice quien le debe a quien.
            when(gastos.saldoDe(anyString(), anyString(), any(), any()))
                    .thenReturn(new BigDecimal("-505.00"));

            SaldoRespuesta r = servicio.saldo(YearMonth.of(2026, 9));

            assertThat(r.monto()).isEqualByComparingTo("505.00");
            assertThat(r.deudorNombre()).isEqualTo("Franco");
            assertThat(r.acreedorNombre()).isEqualTo("Viole");
        }

        @Test
        @DisplayName("en cero no hay deudor ni acreedor")
        void enCero() {
            SaldoRespuesta r = servicio.saldo(YearMonth.of(2026, 9));

            assertThat(r.monto()).isEqualByComparingTo("0.00");
            assertThat(r.deudorNombre()).isNull();
            assertThat(r.acreedorNombre()).isNull();
        }

        @Test
        @DisplayName("solo en el grupo: sin la otra persona, el saldo es cero")
        void sinLaOtraPersona() {
            // Pasa entre que el primero se registra y el segundo entra. Sin
            // esto, buscar al otro integrante devolveria null y la respuesta se
            // armaria con un nombre en null.
            when(usuarios.findByGrupoIdOrderByIdAsc(GRUPO)).thenReturn(List.of(franco));
            when(gastos.saldoDe(anyString(), anyString(), any(), any()))
                    .thenReturn(new BigDecimal("505.00"));

            SaldoRespuesta r = servicio.saldo(YearMonth.of(2026, 9));

            assertThat(r.monto()).isEqualByComparingTo("0.00");
            assertThat(r.deudorNombre()).isNull();
        }
    }

    // ------------------------------------------------------------- utilidades

    private Gasto gasto(Usuario pagador, String monto, String montoPagador,
                        TipoGasto tipo, boolean esHormiga, String categoria) {
        return new Gasto(GRUPO, pagador.comoReferencia(),
                new ReferenciaCategoria("cat-" + categoria.toLowerCase(), categoria, "coffee"),
                new BigDecimal(monto), new BigDecimal(montoPagador),
                tipo, LocalDate.of(2026, 9, 3), "lo que sea", esHormiga, null, null);
    }

    private Usuario usuario(String id, String nombre) {
        Usuario u = new Usuario(nombre, nombre.toLowerCase() + "@local", "hash", GRUPO);
        try {
            Field f = Usuario.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return u;
    }
}
