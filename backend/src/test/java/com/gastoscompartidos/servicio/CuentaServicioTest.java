package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.BorrarCuentaRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.DemasiadosIntentosException;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del borrado de cuenta, con los repositorios mockeados.
 *
 * Lo que prueban es la DECISION: que se borra, que se anonimiza, en que orden, y
 * cuando no se toca nada. Que el `updateMany` con `arrayFilters` de los aportes
 * este bien escrito no lo puede probar un mock -- eso lo cubre el smoke test
 * contra Mongo de verdad.
 *
 * Como en AutenticacionServicioTest, el encoder es un BCrypt real: un servicio
 * que comparara la contrasena en texto plano tiene que fallar aca.
 */
class CuentaServicioTest {

    private static final String GRUPO = "grupo-1";
    private static final String PASSWORD = "una-frase-larga-que-me-acuerdo";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private UsuarioRepositorio usuarios;
    private GrupoRepositorio grupos;
    private GastoRepositorio gastos;
    private PozoRepositorio pozos;
    private CuentaServicio servicio;
    private Usuario franco;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        grupos = mock(GrupoRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        pozos = mock(PozoRepositorio.class);
        UsuarioActual usuarioActual = mock(UsuarioActual.class);

        Clock reloj = Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new CuentaServicio(usuarios, grupos, gastos, pozos,
                new BCryptPasswordEncoder(), new LimitadorDeIntentos(reloj), usuarioActual);

        franco = new Usuario("Franco", "franco@local", HASH, GRUPO);
        escribirCampo(franco, "id", "u-franco");
        when(usuarioActual.requerido()).thenReturn(franco);
    }

    @Test
    @DisplayName("con pareja: borra sus personales, anonimiza lo compartido, y el grupo queda")
    void conPareja() {
        when(usuarios.countByGrupoId(GRUPO)).thenReturn(2L);

        servicio.borrar(new BorrarCuentaRequest(PASSWORD));

        verify(gastos).borrarPersonalesDe(GRUPO, "u-franco");
        verify(gastos).anonimizarPagador(GRUPO, "u-franco", "Cuenta eliminada");
        verify(pozos).anonimizarAportante(GRUPO, "u-franco", "Cuenta eliminada");
        verify(usuarios).delete(franco);

        // Lo que NO puede pasar: llevarse el historial de la otra persona.
        verify(gastos, never()).deleteByGrupoId(anyString());
        verify(pozos, never()).deleteByGrupoId(anyString());
        verify(grupos, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("el ultimo integrante se lleva todo: gastos, vaquitas y grupo")
    void ultimoIntegrante() {
        when(usuarios.countByGrupoId(GRUPO)).thenReturn(1L);

        servicio.borrar(new BorrarCuentaRequest(PASSWORD));

        verify(gastos).deleteByGrupoId(GRUPO);
        verify(pozos).deleteByGrupoId(GRUPO);
        verify(grupos).deleteById(GRUPO);
        verify(usuarios).delete(franco);
        // No queda nadie para quien anonimizar.
        verify(gastos, never()).anonimizarPagador(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("el usuario se borra AL FINAL: si algo falla antes, se puede reintentar")
    void usuarioAlFinal() {
        when(usuarios.countByGrupoId(GRUPO)).thenReturn(2L);

        servicio.borrar(new BorrarCuentaRequest(PASSWORD));

        InOrder orden = inOrder(gastos, pozos, usuarios);
        orden.verify(gastos).borrarPersonalesDe(anyString(), anyString());
        orden.verify(gastos).anonimizarPagador(anyString(), anyString(), anyString());
        orden.verify(pozos).anonimizarAportante(anyString(), anyString(), anyString());
        orden.verify(usuarios).delete(franco);
    }

    @Test
    @DisplayName("con la contrasena equivocada no toca nada, y NO es un 401")
    void contrasenaEquivocada() {
        // ReglaDeNegocioException es un 400. Un 401 haria que la app cerrara la
        // sesion sola, que es como reacciona a un token vencido.
        assertThatThrownBy(() -> servicio.borrar(new BorrarCuentaRequest("otra-cosa-larga-igual")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("contrasena no es correcta");

        verify(usuarios, never()).delete(any());
        verify(gastos, never()).borrarPersonalesDe(anyString(), anyString());
        verify(gastos, never()).deleteByGrupoId(anyString());
    }

    @Test
    @DisplayName("al sexto intento con contrasena equivocada corta el limitador")
    void rateLimit() {
        // Sin esto, un token robado seria un verificador de contrasenas sin limite.
        for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA; i++) {
            try {
                servicio.borrar(new BorrarCuentaRequest("intento-equivocado-" + i));
            } catch (ReglaDeNegocioException esperado) {
                // cada uno es un fallo contado
            }
        }

        assertThatThrownBy(() -> servicio.borrar(new BorrarCuentaRequest(PASSWORD)))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(usuarios, never()).delete(any());
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
