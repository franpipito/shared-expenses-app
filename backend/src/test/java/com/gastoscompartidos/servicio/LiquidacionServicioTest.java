package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.EditarLiquidacionRequest;
import com.gastoscompartidos.dto.RegistrarLiquidacionRequest;
import com.gastoscompartidos.dto.SaldoTotalRespuesta;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Liquidacion;
import com.gastoscompartidos.modelo.ReferenciaUsuario;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.LiquidacionRepositorio;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@code saldo = deudas - pagos}, sin base de datos.
 *
 * Hermano de PozoServicioTest: los repositorios son mocks, asi que esto
 * ejercita la LOGICA (el signo, quien es deudor, que rechaza) y no la
 * consulta de Mongo -- que $subtract este bien armado en saldoHistoricoDe lo
 * sigue probando el smoke test.
 */
class LiquidacionServicioTest {

    private static final String GRUPO = "grupo-1";

    private LiquidacionRepositorio liquidaciones;
    private GastoRepositorio gastos;
    private UsuarioRepositorio usuarios;
    private UsuarioActual usuarioActual;
    private LiquidacionServicio servicio;

    private Usuario franco;
    private Usuario viole;

    @BeforeEach
    void preparar() {
        liquidaciones = mock(LiquidacionRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        Clock reloj = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new LiquidacionServicio(liquidaciones, gastos, usuarios, usuarioActual, reloj);

        franco = usuario("u-franco", "Franco");
        viole = usuario("u-viole", "Viole");
        when(usuarioActual.requerido()).thenReturn(franco);

        // Metodo default de la interfaz: el mock no corre su cuerpo (ver la
        // nota en GastoServicioTest), asi que hay que programarlo a mano.
        when(usuarios.otroIntegranteDe(GRUPO, "u-franco")).thenReturn(viole);
    }

    @Test
    @DisplayName("solo la deuda de los compartidos, sin liquidaciones: el saldo es esa deuda")
    void soloDeudaDeCompartidos() {
        when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(new BigDecimal("1500.00"));
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of());

        SaldoTotalRespuesta r = servicio.saldoTotal();

        assertThat(r.aFavorMio()).isEqualByComparingTo("1500.00");
        assertThat(r.monto()).isEqualByComparingTo("1500.00");
        assertThat(r.acreedorId()).isEqualTo("u-franco");
        assertThat(r.deudorId()).isEqualTo("u-viole");
    }

    @Test
    @DisplayName("una liquidacion que YO pague reduce lo que debo")
    void pagarReduceLoQueDebo() {
        // Franco debe 1000 (aFavorMio negativo), y pago esos 1000.
        when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(new BigDecimal("-1000.00"));
        Liquidacion pago = new Liquidacion(GRUPO, franco.comoReferencia(), viole.comoReferencia(),
                new BigDecimal("1000.00"), LocalDate.of(2026, 9, 15));
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of(pago));

        SaldoTotalRespuesta r = servicio.saldoTotal();

        assertThat(r.aFavorMio()).isEqualByComparingTo("0.00");
        assertThat(r.monto()).isEqualByComparingTo("0.00");
        assertThat(r.deudorId()).isNull();
        assertThat(r.acreedorId()).isNull();
    }

    @Test
    @DisplayName("una liquidacion que recibo baja lo que me deben, no lo sube")
    void recibirBajaLoQueMeDeben() {
        // A Franco le deben 1000, y le pagaron 400: quedan debiendole 600.
        when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(new BigDecimal("1000.00"));
        Liquidacion pago = new Liquidacion(GRUPO, viole.comoReferencia(), franco.comoReferencia(),
                new BigDecimal("400.00"), LocalDate.of(2026, 9, 20));
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of(pago));

        SaldoTotalRespuesta r = servicio.saldoTotal();

        assertThat(r.aFavorMio()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("estando sola, el saldo total es cero y sin nadie a quien deberle")
    void solaSinPareja() {
        when(usuarios.otroIntegranteDe(GRUPO, "u-franco")).thenReturn(null);

        SaldoTotalRespuesta r = servicio.saldoTotal();

        assertThat(r.monto()).isEqualByComparingTo("0.00");
        assertThat(r.deudorId()).isNull();
        assertThat(r.acreedorId()).isNull();
    }

    @Test
    @DisplayName("registrar (yo pague) guarda de=quien pide, para=la otra persona")
    void registrarGuardaLaDireccionCorrecta() {
        when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(new BigDecimal("-1000.00"));
        // Antes de guardar (para armar el saldo actualizado que devuelve registrar()).
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of());

        servicio.registrar(new RegistrarLiquidacionRequest(new BigDecimal("300"), false));

        ArgumentCaptor<Liquidacion> captura = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).save(captura.capture());
        Liquidacion guardada = captura.getValue();

        assertThat(guardada.getDe()).isEqualTo(new ReferenciaUsuario("u-franco", "Franco"));
        assertThat(guardada.getPara()).isEqualTo(new ReferenciaUsuario("u-viole", "Viole"));
        // El cliente podria mandar menos de 2 decimales: se normaliza a 2.
        assertThat(guardada.getMonto()).isEqualByComparingTo("300.00");
        assertThat(guardada.getFecha()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("registrar (me lo pagaron) invierte de y para: la otra persona pago")
    void registrarConMeLoPagaronInvierteLaDireccion() {
        when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(new BigDecimal("1000.00"));
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of());

        servicio.registrar(new RegistrarLiquidacionRequest(new BigDecimal("300"), true));

        ArgumentCaptor<Liquidacion> captura = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).save(captura.capture());
        Liquidacion guardada = captura.getValue();

        assertThat(guardada.getDe()).isEqualTo(new ReferenciaUsuario("u-viole", "Viole"));
        assertThat(guardada.getPara()).isEqualTo(new ReferenciaUsuario("u-franco", "Franco"));
    }

    @Test
    @DisplayName("registrar se rechaza si esta sola: no hay a quien pagarle")
    void registrarRechazaSinPareja() {
        when(usuarios.otroIntegranteDe(GRUPO, "u-franco")).thenReturn(null);

        assertThatThrownBy(() -> servicio.registrar(new RegistrarLiquidacionRequest(new BigDecimal("100"), false)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("otra persona tiene que estar en tu grupo");

        verify(liquidaciones, never()).save(any());
    }

    @Test
    @DisplayName("listar mapea el historial completo")
    void listarMapeaElHistorial() {
        Liquidacion l1 = new Liquidacion(GRUPO, franco.comoReferencia(), viole.comoReferencia(),
                new BigDecimal("100.00"), LocalDate.of(2026, 9, 10));
        Liquidacion l2 = new Liquidacion(GRUPO, viole.comoReferencia(), franco.comoReferencia(),
                new BigDecimal("50.00"), LocalDate.of(2026, 9, 20));
        when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of(l2, l1));

        List<?> respuesta = servicio.listar();

        assertThat(respuesta).hasSize(2);
    }

    @Nested
    @DisplayName("Editar una liquidacion")
    class Editar {

        @Test
        @DisplayName("corrige el monto de verdad, sin dejar un asiento nuevo, y releer el saldo total")
        void corrigeYRelee() {
            Liquidacion existente = new Liquidacion(GRUPO, franco.comoReferencia(), viole.comoReferencia(),
                    new BigDecimal("300.00"), LocalDate.of(2026, 9, 15));
            escribirCampo(existente, "id", "liq-1");
            when(liquidaciones.findByIdAndGrupoId("liq-1", GRUPO)).thenReturn(Optional.of(existente));
            when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(BigDecimal.ZERO.setScale(2));
            when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of(existente));

            servicio.editar("liq-1", new EditarLiquidacionRequest(new BigDecimal("450.00")));

            assertThat(existente.getMonto()).isEqualByComparingTo("450.00");
            verify(liquidaciones).save(existente);
        }

        @Test
        @DisplayName("la puede corregir CUALQUIERA de los dos, no solo quien la registro")
        void laOtraPersonaTambienPuede() {
            // Esta liquidacion la registro Viole (de = Viole); quien pide el
            // cambio es Franco, el usuario actual del mock. A diferencia de un
            // aporte a la vaquita -- donde solo quien aporto puede tocar SU
            // aporte -- esto tiene que andar: una liquidacion es un hecho entre
            // los dos, no la propiedad de quien la tipeo.
            Liquidacion existente = new Liquidacion(GRUPO, viole.comoReferencia(), franco.comoReferencia(),
                    new BigDecimal("300.00"), LocalDate.of(2026, 9, 15));
            escribirCampo(existente, "id", "liq-1");
            when(liquidaciones.findByIdAndGrupoId("liq-1", GRUPO)).thenReturn(Optional.of(existente));
            when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(BigDecimal.ZERO.setScale(2));
            when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of(existente));

            servicio.editar("liq-1", new EditarLiquidacionRequest(new BigDecimal("450.00")));

            assertThat(existente.getMonto()).isEqualByComparingTo("450.00");
            // La direccion no se tocó: sigue siendo Viole -> Franco.
            assertThat(existente.getDe()).isEqualTo(viole.comoReferencia());
            assertThat(existente.getPara()).isEqualTo(franco.comoReferencia());
        }

        @Test
        @DisplayName("una liquidacion inexistente o de otro grupo da 404, sin distinguir cual")
        void noSeDistingueElMotivo() {
            // findByIdAndGrupoId ya exige las dos cosas en la misma consulta: no
            // hay forma de llegar a editar una liquidacion de otro grupo ni de
            // confirmar por el codigo de error si el id existe en otro lado.
            when(liquidaciones.findByIdAndGrupoId("liq-ajena", GRUPO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.editar("liq-ajena", new EditarLiquidacionRequest(new BigDecimal("1.00"))))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(liquidaciones, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Borrar una liquidacion")
    class Borrar {

        @Test
        @DisplayName("la borra y releer el saldo total")
        void borraYRelee() {
            when(liquidaciones.deleteByIdAndGrupoId("liq-1", GRUPO)).thenReturn(1L);
            when(gastos.saldoHistoricoDe(GRUPO, "u-franco")).thenReturn(BigDecimal.ZERO.setScale(2));
            when(liquidaciones.findByGrupoIdOrderByFechaDescIdDesc(GRUPO)).thenReturn(List.of());

            servicio.borrar("liq-1");

            verify(liquidaciones).deleteByIdAndGrupoId("liq-1", GRUPO);
        }

        @Test
        @DisplayName("una liquidacion inexistente o de otro grupo da 404, sin distinguir cual")
        void noSeDistingueElMotivo() {
            when(liquidaciones.deleteByIdAndGrupoId("liq-ajena", GRUPO)).thenReturn(0L);

            assertThatThrownBy(() -> servicio.borrar("liq-ajena"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    private static Usuario usuario(String id, String nombre) {
        Usuario u = new Usuario(nombre, nombre.toLowerCase() + "@local", "hash", GRUPO);
        escribirCampo(u, "id", id);
        return u;
    }

    private static void escribirCampo(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
