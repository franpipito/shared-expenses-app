package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.AporteRequest;
import com.gastoscompartidos.dto.CrearPozoRequest;
import com.gastoscompartidos.dto.PozoRespuesta;
import com.gastoscompartidos.dto.TotalPorPersona;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Aporte;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Pozo;
import com.gastoscompartidos.modelo.ReferenciaUsuario;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las reglas de la vaquita, sin base de datos.
 *
 * Hermano de `GastoServicioTest`: los repositorios son mocks, asi que lo que se
 * ejercita es la logica del servicio y no la consulta de Mongo. Lo que un mock
 * no puede probar -- que el indice parcial unico exista de verdad, que `$push`
 * sea atomico -- lo sigue cubriendo `scripts/smoke-test.ps1`.
 *
 * Eso se ve bien en `unaSolaAbierta`: aca se puede verificar que el servicio
 * TRADUZCA la `DuplicateKeyException` del indice a un mensaje util, pero que el
 * indice rechace el segundo pozo solo lo prueba una base real.
 */
class PozoServicioTest {

    private static final String GRUPO = "grupo-1";

    private PozoRepositorio pozos;
    private GastoRepositorio gastos;
    private UsuarioRepositorio usuarios;
    private UsuarioActual usuarioActual;
    private PozoServicio servicio;

    private Usuario franco;
    private Usuario viole;

    @BeforeEach
    void preparar() {
        pozos = mock(PozoRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        Clock reloj = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new PozoServicio(pozos, gastos, usuarios, usuarioActual, reloj);

        franco = usuario("u-franco", "Franco");
        viole = usuario("u-viole", "Viole");

        when(usuarioActual.requerido()).thenReturn(franco);
        when(usuarios.findByGrupoIdOrderByIdAsc(GRUPO)).thenReturn(List.of(franco, viole));
        // Metodo default de la interfaz: el mock no corre su cuerpo. Ver la
        // nota en GastoServicioTest.
        when(usuarios.tienePareja(GRUPO)).thenReturn(true);
        // save() devuelve el documento CON id, que es lo que hace Mongo. Sin
        // eso el pozo recien creado sale con id null y lo que se rompe despues
        // no tiene nada que ver con el bug: la consulta de lo gastado recibe
        // null y devuelve null, y el restante explota en un NullPointer.
        when(pozos.save(any(Pozo.class))).thenAnswer(inv -> {
            Pozo guardado = inv.getArgument(0);
            escribirCampo(guardado, "id", "pozo-nuevo");
            return guardado;
        });
    }

    @Nested
    @DisplayName("Abrir la vaquita")
    class Abrir {

        @Test
        @DisplayName("nace abierta, sin plata y sin nada gastado")
        void naceVacia() {
            when(pozos.findByGrupoIdAndEstado(GRUPO, EstadoPozo.ABIERTO)).thenReturn(Optional.empty());
            when(gastos.sumarDelPozo(anyString())).thenReturn(BigDecimal.ZERO.setScale(2));

            PozoRespuesta r = servicio.crear(
                    new CrearPozoRequest("Bariloche", new BigDecimal("800000.00"), null, null));

            assertThat(r.estado()).isEqualTo(EstadoPozo.ABIERTO);
            assertThat(r.aportado()).isEqualByComparingTo("0.00");
            assertThat(r.gastado()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("con una abierta ya, no deja abrir otra")
        void unaSolaAbiertaChequeoAmable() {
            when(pozos.findByGrupoIdAndEstado(GRUPO, EstadoPozo.ABIERTO))
                    .thenReturn(Optional.of(pozo("pozo-viejo", EstadoPozo.ABIERTO)));

            assertThatThrownBy(() -> servicio.crear(
                    new CrearPozoRequest("Otra", null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Cerrá la anterior");

            // Ni siquiera intenta escribir: el chequeo previo esta para dar un
            // mensaje util, no para ahorrarse el indice.
            verify(pozos, never()).save(any());
        }

        @Test
        @DisplayName("si dos abren a la vez, el indice gana y el mensaje sigue siendo el util")
        void unaSolaAbiertaLaGarantiaEsElIndice() {
            // Entre el chequeo de arriba y el save() hay una ventana: dos
            // requests simultaneas pasarian las dos. La garantia de verdad es el
            // indice parcial unico, y lo que se prueba aca es que su excepcion
            // no se escape como un 500 sino como el mismo mensaje entendible.
            when(pozos.findByGrupoIdAndEstado(GRUPO, EstadoPozo.ABIERTO)).thenReturn(Optional.empty());
            when(pozos.save(any(Pozo.class))).thenThrow(new DuplicateKeyException("indice parcial"));

            assertThatThrownBy(() -> servicio.crear(
                    new CrearPozoRequest("Bariloche", null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Cerrá la anterior");
        }

        @Test
        @DisplayName("la fecha de fin no puede ser anterior a la de inicio")
        void fechasAlReves() {
            assertThatThrownBy(() -> servicio.crear(new CrearPozoRequest(
                    "Bariloche", null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1))))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no puede ser anterior");
        }

        @Test
        @DisplayName("con un solo integrante en el grupo no se puede abrir")
        void sinPareja() {
            when(usuarios.tienePareja(GRUPO)).thenReturn(false);

            assertThatThrownBy(() -> servicio.crear(
                    new CrearPozoRequest("Bariloche", null, null, null)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("la otra persona tiene que estar en tu grupo");

            verify(pozos, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Quien borro su cuenta")
    class ExIntegrante {

        @Test
        @DisplayName("sus aportes siguen en el desglose por persona, con el nombre anonimizado")
        void aportesDeQuienSeFue() {
            // Viole y Franco en el grupo; un tercer id que ya no es integrante
            // (borro su cuenta) con un aporte que quedo en el pozo. Sin esto,
            // su plata sumaba en "aportado" pero no aparecia en ninguna fila.
            Pozo p = pozo("pozo-1", EstadoPozo.ABIERTO);
            var ex = new ReferenciaUsuario("u-ex", "Cuenta eliminada");
            escribirCampo(p, "aportes", new ArrayList<>(List.of(
                    new Aporte(franco.comoReferencia(), new BigDecimal("400000.00"), LocalDate.of(2026, 9, 1)),
                    new Aporte(ex, new BigDecimal("300000.00"), LocalDate.of(2026, 9, 1)),
                    new Aporte(ex, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 2)))));
            when(pozos.findByGrupoIdOrderByCreadoEnDesc(GRUPO)).thenReturn(List.of(p));
            when(gastos.sumarDelPozo(anyString())).thenReturn(BigDecimal.ZERO.setScale(2));

            var porPersona = servicio.listar().get(0).porPersona();

            assertThat(porPersona).extracting("nombre")
                    .containsExactly("Franco", "Viole", "Cuenta eliminada");
            // Una sola fila por persona aunque haya aportado dos veces.
            assertThat(porPersona.get(2).total()).isEqualByComparingTo("400000.00");
        }
    }

    @Nested
    @DisplayName("Quien gasto cuanto")
    class QuienGastoCuanto {

        @Test
        @DisplayName("se listan los dos integrantes, incluido el que gasto cero")
        void losDosSiempre() {
            Pozo p = pozo("pozo-1", EstadoPozo.ABIERTO);
            when(pozos.findByGrupoIdOrderByCreadoEnDesc(GRUPO)).thenReturn(List.of(p));
            when(gastos.sumarDelPozo(anyString())).thenReturn(new BigDecimal("150000.00"));
            when(gastos.gastadoPorPersonaDelPozo(anyString())).thenReturn(List.of(
                    new TotalPorPersona(franco.getId(), "Franco", new BigDecimal("150000.00"))));

            var gastadoPorPersona = servicio.listar().get(0).gastadoPorPersona();

            assertThat(gastadoPorPersona).extracting("nombre").containsExactly("Franco", "Viole");
            // Viole no gasto nada del pozo: aparece con cero, no desaparece.
            assertThat(gastadoPorPersona.get(1).total()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("quien gasto del pozo y ya no esta en el grupo sigue en el desglose")
        void deQuienSeFue() {
            // Mismo caso que aportesDeQuienSeFue, pero del lado de los
            // debitos: alguien gasto del pozo y despues borro su cuenta o
            // salio del grupo. Sin esto su gasto seguiria sumando en
            // "gastado" pero no aparecería en ninguna fila.
            Pozo p = pozo("pozo-1", EstadoPozo.ABIERTO);
            when(pozos.findByGrupoIdOrderByCreadoEnDesc(GRUPO)).thenReturn(List.of(p));
            when(gastos.sumarDelPozo(anyString())).thenReturn(new BigDecimal("300000.00"));
            when(gastos.gastadoPorPersonaDelPozo(anyString())).thenReturn(List.of(
                    new TotalPorPersona(franco.getId(), "Franco", new BigDecimal("100000.00")),
                    new TotalPorPersona("u-ex", "Cuenta eliminada", new BigDecimal("200000.00"))));

            var gastadoPorPersona = servicio.listar().get(0).gastadoPorPersona();

            assertThat(gastadoPorPersona).extracting("nombre")
                    .containsExactly("Franco", "Viole", "Cuenta eliminada");
            assertThat(gastadoPorPersona.get(2).total()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("es puramente informativo: no cambia gastado ni restante")
        void noGeneraDeudaNueva() {
            // Franco gasto TODO el pozo y Viole nada -- esa diferencia no
            // tiene que generar ninguna deuda nueva. El invariante sigue
            // siendo aportado - gastado, sin importar quien pago que.
            Pozo p = pozo("pozo-1", EstadoPozo.ABIERTO);
            escribirCampo(p, "aportes", new ArrayList<>(List.of(
                    new Aporte(franco.comoReferencia(), new BigDecimal("400000.00"), LocalDate.of(2026, 9, 1)),
                    new Aporte(viole.comoReferencia(), new BigDecimal("400000.00"), LocalDate.of(2026, 9, 1)))));
            when(pozos.findByGrupoIdOrderByCreadoEnDesc(GRUPO)).thenReturn(List.of(p));
            when(gastos.sumarDelPozo(anyString())).thenReturn(new BigDecimal("300000.00"));
            when(gastos.gastadoPorPersonaDelPozo(anyString())).thenReturn(List.of(
                    new TotalPorPersona(franco.getId(), "Franco", new BigDecimal("300000.00")),
                    new TotalPorPersona(viole.getId(), "Viole", BigDecimal.ZERO)));

            PozoRespuesta r = servicio.listar().get(0);

            assertThat(r.gastado()).isEqualByComparingTo("300000.00");
            assertThat(r.restante()).isEqualByComparingTo("500000.00");
        }
    }

    @Nested
    @DisplayName("Aportar")
    class Aportar {

        @Test
        @DisplayName("el aporte va SIEMPRE a nombre de quien lo hace")
        void aNombreDeQuienAporta() {
            // AporteRequest ni siquiera tiene un campo para el usuario: un
            // aporte es la afirmacion "puse esta plata", y eso solo lo puede
            // decir quien la puso.
            when(pozos.agregarAporte(anyString(), anyString(), any())).thenReturn(true);
            when(pozos.findByIdAndGrupoId("pozo-1", GRUPO))
                    .thenReturn(Optional.of(pozo("pozo-1", EstadoPozo.ABIERTO)));
            when(gastos.sumarDelPozo(anyString())).thenReturn(BigDecimal.ZERO.setScale(2));

            servicio.aportar("pozo-1", new AporteRequest(new BigDecimal("400000.00")));

            ArgumentCaptor<Aporte> capturado = ArgumentCaptor.forClass(Aporte.class);
            verify(pozos).agregarAporte(eq("pozo-1"), eq(GRUPO), capturado.capture());
            assertThat(capturado.getValue().usuario().usuarioId()).isEqualTo(franco.getId());
            assertThat(capturado.getValue().monto()).isEqualByComparingTo("400000.00");
        }

        @Test
        @DisplayName("un aporte negativo se permite: es como se deshace uno equivocado")
        void negativoEsElAsientoEnContrario() {
            // Los aportes son inmutables a proposito -- no se editan ni se
            // borran -- asi que sin el negativo, tipear 4000000 en vez de 400000
            // dejaba el pozo con esa plata para siempre.
            when(pozos.agregarAporte(anyString(), anyString(), any())).thenReturn(true);
            when(pozos.findByIdAndGrupoId("pozo-1", GRUPO))
                    .thenReturn(Optional.of(pozo("pozo-1", EstadoPozo.ABIERTO)));
            when(gastos.sumarDelPozo(anyString())).thenReturn(BigDecimal.ZERO.setScale(2));

            servicio.aportar("pozo-1", new AporteRequest(new BigDecimal("-3600000.00")));

            ArgumentCaptor<Aporte> capturado = ArgumentCaptor.forClass(Aporte.class);
            verify(pozos).agregarAporte(anyString(), anyString(), capturado.capture());
            assertThat(capturado.getValue().monto()).isEqualByComparingTo("-3600000.00");
        }

        @Test
        @DisplayName("cero se rechaza: no es aporte ni correccion")
        void ceroNoEsNada() {
            assertThatThrownBy(() -> servicio.aportar("pozo-1", new AporteRequest(BigDecimal.ZERO)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no puede ser cero");

            verify(pozos, never()).agregarAporte(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("aportar a una vaquita cerrada o ajena da 404, sin distinguir cual")
        void noSeDistingueElMotivo() {
            // agregarAporte verifica id, grupo y estado en una sola operacion
            // atomica. Si devuelve false no sabemos cual de las tres fallo, y
            // esta bien: distinguirlas confirmaria que el pozo existe.
            when(pozos.agregarAporte(anyString(), anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> servicio.aportar("pozo-x", new AporteRequest(new BigDecimal("100"))))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("Cerrar")
    class Cerrar {

        @Test
        @DisplayName("cerrar dos veces no es un exito silencioso")
        void cerrarDosVeces() {
            // El segundo cierre no encuentra ninguna ABIERTA con ese id, asi que
            // la escritura condicional devuelve false. Responder 200 igual haria
            // creer que paso algo que no paso.
            when(pozos.cerrar("pozo-1", GRUPO)).thenReturn(false);

            assertThatThrownBy(() -> servicio.cerrar("pozo-1"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------- utilidades

    private Usuario usuario(String id, String nombre) {
        Usuario u = new Usuario(nombre, nombre.toLowerCase() + "@local", "hash", GRUPO);
        escribirCampo(u, "id", id);
        return u;
    }

    private Pozo pozo(String id, EstadoPozo estado) {
        Pozo p = new Pozo(GRUPO, "Bariloche", new BigDecimal("800000.00"), null, null);
        escribirCampo(p, "id", id);
        if (estado != EstadoPozo.ABIERTO) {
            escribirCampo(p, "estado", estado);
        }
        return p;
    }

    /** Ver la nota de GastoServicioTest: el id lo pone Mongo y no hay setter. */
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
