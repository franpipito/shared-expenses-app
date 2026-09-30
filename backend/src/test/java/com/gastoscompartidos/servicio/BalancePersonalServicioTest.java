package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.BalancePersonalRespuesta;
import com.gastoscompartidos.dto.RegistrarIngresoRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Ingreso;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
 * Tests de "Mi Plata" (sección 2.3b), sin base de datos.
 *
 * Mismo espíritu que `PozoServicioTest`: los repositorios son mocks, así que
 * lo que se ejercita es la lógica del servicio -- que el negativo invierta,
 * que el cero se rechace, que el restante pueda dar negativo sin explotar.
 * Que el `$push` sea atómico de verdad lo sigue cubriendo
 * `scripts/smoke-test.ps1`.
 */
class BalancePersonalServicioTest {

    private static final String GRUPO = "grupo-1";

    private UsuarioRepositorio usuarios;
    private GastoRepositorio gastos;
    private UsuarioActual usuarioActual;
    private BalancePersonalServicio servicio;

    private Usuario viole;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        Clock reloj = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new BalancePersonalServicio(usuarios, gastos, usuarioActual, reloj);

        viole = usuario("u-viole", "Viole");
        when(usuarioActual.requerido()).thenReturn(viole);
    }

    @Nested
    @DisplayName("Ver el balance")
    class Ver {

        @Test
        @DisplayName("restante = ingresado - gastado")
        void restaBien() {
            escribirCampo(viole, "ingresos", new ArrayList<>(List.of(
                    new Ingreso(new BigDecimal("80000.00"), java.time.LocalDate.of(2026, 9, 1)))));
            when(gastos.totalPersonalDe(GRUPO, "u-viole")).thenReturn(new BigDecimal("25000.00"));

            BalancePersonalRespuesta r = servicio.ver();

            assertThat(r.ingresado()).isEqualByComparingTo("80000.00");
            assertThat(r.gastado()).isEqualByComparingTo("25000.00");
            assertThat(r.restante()).isEqualByComparingTo("55000.00");
        }

        @Test
        @DisplayName("el restante puede dar negativo, y no explota")
        void restanteNegativoNoExplota() {
            escribirCampo(viole, "ingresos", new ArrayList<>(List.of(
                    new Ingreso(new BigDecimal("10000.00"), java.time.LocalDate.of(2026, 9, 1)))));
            when(gastos.totalPersonalDe(GRUPO, "u-viole")).thenReturn(new BigDecimal("40000.00"));

            BalancePersonalRespuesta r = servicio.ver();

            assertThat(r.restante()).isEqualByComparingTo("-30000.00");
        }
    }

    @Nested
    @DisplayName("Agregar un ingreso")
    class Agregar {

        @Test
        @DisplayName("se agrega con la fecha de hoy y se relee el usuario para responder")
        void seAgregaYRelee() {
            when(gastos.totalPersonalDe(anyString(), anyString())).thenReturn(BigDecimal.ZERO.setScale(2));
            when(usuarios.findById("u-viole")).thenReturn(Optional.of(viole));

            servicio.agregarIngreso(new RegistrarIngresoRequest(new BigDecimal("50000.00")));

            ArgumentCaptor<Ingreso> capturado = ArgumentCaptor.forClass(Ingreso.class);
            verify(usuarios).agregarIngreso(eq("u-viole"), capturado.capture());
            assertThat(capturado.getValue().monto()).isEqualByComparingTo("50000.00");
            assertThat(capturado.getValue().fecha()).isEqualTo(java.time.LocalDate.of(2026, 9, 6));
        }

        @Test
        @DisplayName("un ingreso negativo se permite: es como se deshace uno equivocado")
        void negativoEsElAsientoEnContrario() {
            when(gastos.totalPersonalDe(anyString(), anyString())).thenReturn(BigDecimal.ZERO.setScale(2));
            when(usuarios.findById("u-viole")).thenReturn(Optional.of(viole));

            servicio.agregarIngreso(new RegistrarIngresoRequest(new BigDecimal("-5000.00")));

            ArgumentCaptor<Ingreso> capturado = ArgumentCaptor.forClass(Ingreso.class);
            verify(usuarios).agregarIngreso(anyString(), capturado.capture());
            assertThat(capturado.getValue().monto()).isEqualByComparingTo("-5000.00");
        }

        @Test
        @DisplayName("cero se rechaza: no es ingreso ni correccion")
        void ceroNoEsNada() {
            assertThatThrownBy(() -> servicio.agregarIngreso(new RegistrarIngresoRequest(BigDecimal.ZERO)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no puede ser cero");

            verify(usuarios, never()).agregarIngreso(anyString(), any());
        }
    }

    // ------------------------------------------------------------- utilidades

    private Usuario usuario(String id, String nombre) {
        Usuario u = new Usuario(nombre, nombre.toLowerCase() + "@local", "hash", GRUPO);
        escribirCampo(u, "id", id);
        return u;
    }

    /** Ver la nota de PozoServicioTest: el id lo pone Mongo y no hay setter. */
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
