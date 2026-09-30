package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.GrupoRespuesta;
import com.gastoscompartidos.dto.InvitacionRespuesta;
import com.gastoscompartidos.dto.SalirDelGrupoRequest;
import com.gastoscompartidos.dto.SumarseRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.DemasiadosIntentosException;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
 * Tests de invitar / sumarse / salir, sin base de datos.
 *
 * Igual que en PozoServicioTest y CuentaServicioTest: los repositorios son
 * mocks, asi que esto ejercita la LOGICA (que se rechaza, que se mueve, en que
 * orden) y no la consulta de Mongo. Que `consumirInvitacion` sea de verdad
 * atomico, y que el indice parcial unico del codigo exista, lo sigue probando
 * el smoke test contra una base real.
 */
class GrupoServicioTest {

    private static final String GRUPO_FRANCO = "grupo-franco";
    private static final String GRUPO_VIOLE = "grupo-viole";
    private static final String PASSWORD = "una-frase-larga-que-me-acuerdo";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");

    private GrupoRepositorio grupos;
    private UsuarioRepositorio usuarios;
    private GastoRepositorio gastos;
    private UsuarioActual usuarioActual;
    private GrupoServicio servicio;

    private Usuario franco;

    @BeforeEach
    void preparar() {
        grupos = mock(GrupoRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);

        Clock reloj = Clock.fixed(AHORA, ZoneId.of("America/Argentina/Buenos_Aires"));

        servicio = new GrupoServicio(grupos, usuarios, gastos,
                new BCryptPasswordEncoder(), new LimitadorDeIntentos(reloj), usuarioActual, reloj, "Casa");

        franco = new Usuario("Franco", "franco@local", HASH, GRUPO_FRANCO);
        escribirCampo(franco, "id", "u-franco");
        when(usuarioActual.requerido()).thenReturn(franco);

        // save() devuelve el documento CON id, que es lo que hace Mongo. Ver
        // la nota en PozoServicioTest: sin esto, el id sale null y lo que se
        // rompe despues no tiene nada que ver con lo que se esta probando.
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Invitar")
    class Invitar {

        @BeforeEach
        void grupoDeFranco() {
            Grupo grupo = new Grupo("Casa");
            escribirCampo(grupo, "id", GRUPO_FRANCO);
            when(grupos.findById(GRUPO_FRANCO)).thenReturn(Optional.of(grupo));
            when(grupos.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("genera un codigo de 8 caracteres que vence en 7 dias")
        void generaCodigo() {
            when(usuarios.countByGrupoId(GRUPO_FRANCO)).thenReturn(1L);

            InvitacionRespuesta r = servicio.invitar();

            assertThat(r.codigo()).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}");
            assertThat(r.vence()).isEqualTo(AHORA.plus(GrupoServicio.VIGENCIA_INVITACION));
        }

        @Test
        @DisplayName("rechaza si el grupo ya tiene a las dos personas")
        void rechazaSiYaEstaCompleto() {
            when(usuarios.countByGrupoId(GRUPO_FRANCO)).thenReturn((long) GrupoServicio.MAX_INTEGRANTES);

            assertThatThrownBy(servicio::invitar)
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya tiene a las dos personas");

            verify(grupos, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Sumarse")
    class Sumarse {

        @Test
        @DisplayName("se suma al grupo del codigo y muda sus PERSONAL con el")
        void seSuma() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(false);

            Grupo grupoDeViole = new Grupo("Casa");
            escribirCampo(grupoDeViole, "id", GRUPO_VIOLE);
            when(grupos.consumirInvitacion("ABCD2345", AHORA)).thenReturn(Optional.of(grupoDeViole));
            when(usuarios.findByGrupoIdOrderByIdAsc(GRUPO_VIOLE)).thenReturn(List.of(franco));

            GrupoRespuesta r = servicio.sumarse(new SumarseRequest("abcd2345"));

            // El codigo se normaliza a mayusculas antes de buscarlo: asi da
            // igual como lo haya tipeado o pegado la persona.
            assertThat(r.id()).isEqualTo(GRUPO_VIOLE);
            verify(gastos).moverPersonalesA(GRUPO_FRANCO, "u-franco", GRUPO_VIOLE);
            verify(usuarios).save(franco);
            assertThat(franco.getGrupoId()).isEqualTo(GRUPO_VIOLE);
            // El grupo de uno que dejo atras, ahora vacio, se limpia.
            verify(grupos).deleteById(GRUPO_FRANCO);
        }

        @Test
        @DisplayName("rechaza sumarse si ya esta en un grupo compartido")
        void rechazaSiYaTienePareja() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(true);

            assertThatThrownBy(() -> servicio.sumarse(new SumarseRequest("cualquiera")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya estás en un grupo compartido");

            verify(grupos, never()).consumirInvitacion(anyString(), any());
            verify(gastos, never()).moverPersonalesA(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("codigo invalido o vencido: no mueve nada")
        void codigoInvalido() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(false);
            when(grupos.consumirInvitacion("ZZZZ9999", AHORA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.sumarse(new SumarseRequest("ZZZZ9999")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no es válido o venció");

            verify(gastos, never()).moverPersonalesA(anyString(), anyString(), anyString());
            verify(usuarios, never()).save(any());
            verify(grupos, never()).deleteById(anyString());
        }
    }

    @Nested
    @DisplayName("Salir")
    class Salir {

        @BeforeEach
        void grupoNuevoAlSalir() {
            when(grupos.save(any(Grupo.class))).thenAnswer(inv -> {
                Grupo nuevo = inv.getArgument(0);
                escribirCampo(nuevo, "id", "grupo-nuevo-de-franco");
                return nuevo;
            });
        }

        @Test
        @DisplayName("crea un grupo propio y muda sus PERSONAL, sin tocar al resto")
        void sale() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(true);
            when(usuarios.findByGrupoIdOrderByIdAsc("grupo-nuevo-de-franco")).thenReturn(List.of(franco));

            GrupoRespuesta r = servicio.salir(new SalirDelGrupoRequest(PASSWORD));

            assertThat(r.id()).isEqualTo("grupo-nuevo-de-franco");
            assertThat(franco.getGrupoId()).isEqualTo("grupo-nuevo-de-franco");
            verify(gastos).moverPersonalesA(GRUPO_FRANCO, "u-franco", "grupo-nuevo-de-franco");
            verify(usuarios).save(franco);

            // Lo que NO puede pasar: tocar el historial compartido que queda
            // con la otra persona. Salir no anonimiza ni borra nada de eso.
            verify(gastos, never()).anonimizarPagador(anyString(), anyString(), anyString());
            verify(gastos, never()).borrarPersonalesDe(anyString(), anyString());
        }

        @Test
        @DisplayName("rechaza salir si esta solo: no hay de que salir")
        void rechazaSiEstaSolo() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(false);

            assertThatThrownBy(() -> servicio.salir(new SalirDelGrupoRequest(PASSWORD)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("no hay de qué salir");

            verify(grupos, never()).save(any());
            verify(gastos, never()).moverPersonalesA(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("con la contrasena equivocada no toca nada, y NO es un 401")
        void contrasenaEquivocada() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(true);

            // ReglaDeNegocioException es un 400. Un 401 haria que la app
            // cerrara la sesion sola, igual que en borrar-cuenta.
            assertThatThrownBy(() -> servicio.salir(new SalirDelGrupoRequest("otra-cosa-larga-igual")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("contraseña no es correcta");

            verify(grupos, never()).save(any());
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("al sexto intento con contrasena equivocada corta el limitador")
        void rateLimit() {
            when(usuarios.tienePareja(GRUPO_FRANCO)).thenReturn(true);

            for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA; i++) {
                try {
                    servicio.salir(new SalirDelGrupoRequest("intento-equivocado-" + i));
                } catch (ReglaDeNegocioException esperado) {
                    // cada uno es un fallo contado
                }
            }

            assertThatThrownBy(() -> servicio.salir(new SalirDelGrupoRequest(PASSWORD)))
                    .isInstanceOf(DemasiadosIntentosException.class);
            verify(usuarios, never()).save(any());
        }
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
