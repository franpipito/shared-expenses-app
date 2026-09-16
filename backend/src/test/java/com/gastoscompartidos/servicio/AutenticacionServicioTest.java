package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.LoginRequest;
import com.gastoscompartidos.dto.RegistroRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.NoAutenticadoException;
import com.gastoscompartidos.seguridad.ServicioDeTokens;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las reglas de registro y login.
 *
 * Son las reglas de SEGURIDAD del backend, y hasta aca lo unico que las cubria
 * era el smoke test, que necesita la app corriendo.
 *
 * UNA DECISION SOBRE EL PASSWORD ENCODER: se usa un `BCryptPasswordEncoder` de
 * verdad y no un mock. Mockearlo dejaria pasar un test aunque el servicio
 * comparara contrasenas en texto plano, que es exactamente lo que no queremos
 * que pase nunca. BCrypt con el costo por defecto tarda ~100ms por hash, asi
 * que son unos pocos cientos de milisegundos en total -- barato para lo que
 * compra.
 */
class AutenticacionServicioTest {

    private static final String CODIGO = "codigo-de-prueba";
    private static final String GRUPO = "grupo-1";
    private static final String PASSWORD = "una-frase-larga-que-me-acuerdo";
    private static final String IP = "127.0.0.1";

    /**
     * El hash de PASSWORD, calculado UNA vez y con un encoder propio.
     *
     * Dos motivos, y el segundo es el que importa. El obvio: BCrypt tarda ~100ms
     * y los fixtures lo necesitan en casi todos los tests.
     *
     * El de fondo: si los fixtures usaran el `codificador` del servicio -- que es
     * un spy -- terminarian llamandolo adentro de un `when(...)`, y Mockito no
     * puede distinguir eso de una stubbing a medio terminar. Falla con un
     * `UnfinishedStubbing` que no dice nada del problema real. El spy esta para
     * observar lo que hace EL SERVICIO; armar datos de prueba no es eso.
     */
    private static final String HASH_DE_PASSWORD =
            new BCryptPasswordEncoder().encode("una-frase-larga-que-me-acuerdo");

    private UsuarioRepositorio usuarios;
    private GrupoRepositorio grupos;
    private ServicioDeTokens tokens;
    private UsuarioActual usuarioActual;
    private AutenticacionServicio servicio;
    private PasswordEncoder codificador;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        grupos = mock(GrupoRepositorio.class);
        tokens = mock(ServicioDeTokens.class);
        usuarioActual = mock(UsuarioActual.class);
        // SPY y no mock: por dentro es un BCryptPasswordEncoder de verdad, asi
        // que un servicio que comparara contrasenas en texto plano fallaria los
        // tests igual. Lo que el spy agrega es poder CONTAR las llamadas, que
        // es como se verifica el hash senuelo sin medir tiempo.
        codificador = spy(new BCryptPasswordEncoder());

        Clock reloj = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        // El limitador es real y no un mock: es una clase con estado propio, sin
        // dependencias mas que el reloj, y programarle las respuestas seria
        // reimplementarlo adentro del test.
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(reloj);

        when(tokens.emitirPara(anyString(), anyLong())).thenReturn("un.token.firmado");
        when(tokens.duracion()).thenReturn(Duration.ofDays(30));
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) escribirCampo(u, "id", "u-nuevo");
            return u;
        });
        when(grupos.save(any(Grupo.class))).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            escribirCampo(g, "id", GRUPO);
            return g;
        });

        servicio = new AutenticacionServicio(usuarios, grupos, codificador, tokens,
                limitador, usuarioActual, reloj, CODIGO, "Casa");
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("el primero que se registra CREA el grupo")
        void elPrimeroCreaElGrupo() {
            when(grupos.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

            servicio.registrar(registro("Franco", "franco@local"), IP);

            verify(grupos).save(any(Grupo.class));
        }

        @Test
        @DisplayName("el segundo se suma al que ya existe, no crea otro")
        void elSegundoSeSuma() {
            when(grupos.findFirstByOrderByIdAsc()).thenReturn(Optional.of(grupo()));
            when(usuarios.countByGrupoId(GRUPO)).thenReturn(1L);
            when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

            servicio.registrar(registro("Viole", "viole@local"), IP);

            verify(grupos, never()).save(any(Grupo.class));
            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).save(guardado.capture());
            assertThat(guardado.getValue().getGrupoId()).isEqualTo(GRUPO);
        }

        @Test
        @DisplayName("un tercero se rechaza: el modelo de reparto asume dos")
        void unTerceroSeRechaza() {
            when(grupos.findFirstByOrderByIdAsc()).thenReturn(Optional.of(grupo()));
            when(usuarios.countByGrupoId(GRUPO)).thenReturn(2L);
            when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.registrar(registro("Tercero", "tercero@local"), IP))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("grupo ya esta completo");

            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("sin el codigo de invitacion no se puede registrar")
        void sinCodigo() {
            assertThatThrownBy(() -> servicio.registrar(
                    new RegistroRequest("Intruso", "intruso@local", PASSWORD, "otro-codigo"), IP))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("codigo de invitacion");

            // Y ni siquiera mira si el email existe: el codigo se chequea
            // primero, asi que el registro no puede usarse para averiguar que
            // cuentas hay.
            verify(usuarios, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("la contrasena se guarda hasheada, nunca en texto plano")
        void passwordHasheada() {
            when(grupos.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

            servicio.registrar(registro("Franco", "franco@local"), IP);

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).save(guardado.capture());
            String hash = guardado.getValue().getPasswordHash();

            assertThat(hash).isNotEqualTo(PASSWORD);
            assertThat(hash).startsWith("$2");            // el prefijo de BCrypt
            assertThat(codificador.matches(PASSWORD, hash)).isTrue();
        }

        @Test
        @DisplayName("el email se normaliza: mayusculas y espacios no crean cuentas distintas")
        void emailNormalizado() {
            when(grupos.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

            servicio.registrar(registro("Franco", "  FRANCO@Local  "), IP);

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).save(guardado.capture());
            assertThat(guardado.getValue().getEmail()).isEqualTo("franco@local");
        }

        @Test
        @DisplayName("una contrasena corta se rechaza antes de tocar la base")
        void contrasenaCorta() {
            assertThatThrownBy(() -> servicio.registrar(
                    new RegistroRequest("Franco", "franco@local", "corta", CODIGO), IP))
                    .isInstanceOf(ReglaDeNegocioException.class);

            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("el mismo email dos veces se rechaza")
        void emailRepetido() {
            when(usuarios.findByEmail("franco@local"))
                    .thenReturn(Optional.of(usuarioGuardado("franco@local")));

            assertThatThrownBy(() -> servicio.registrar(registro("Franco", "franco@local"), IP))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("ya esta registrado");
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("con la contrasena correcta devuelve token y usuario")
        void loginOk() {
            when(usuarios.findByEmail("franco@local"))
                    .thenReturn(Optional.of(usuarioGuardado("franco@local")));

            var r = servicio.login(new LoginRequest("franco@local", PASSWORD), IP);

            assertThat(r.token()).isEqualTo("un.token.firmado");
            assertThat(r.usuario().nombre()).isEqualTo("Franco");
        }

        @Test
        @DisplayName("email inexistente y contrasena mala dan EL MISMO error")
        void mismoMensajeParaLosDosCasos() {
            // Si el mensaje distinguiera los casos, el login seria un
            // verificador de que cuentas estan registradas.
            when(usuarios.findByEmail("nadie@local")).thenReturn(Optional.empty());
            when(usuarios.findByEmail("franco@local"))
                    .thenReturn(Optional.of(usuarioGuardado("franco@local")));

            String sinCuenta = mensajeDeFallo("nadie@local", PASSWORD);
            String malaPassword = mensajeDeFallo("franco@local", "otra-cosa-larga-igual");

            assertThat(sinCuenta).isEqualTo(malaPassword);
        }

        @Test
        @DisplayName("con un email que no existe TAMBIEN se verifica un hash")
        void hashSenuelo() {
            // Es la defensa contra el ataque de tiempo: si con un email
            // inexistente el servicio volviera de una, responder rapido seria
            // "esa cuenta no existe" y responder lento "existe, erraste la
            // clave". Por eso compara contra un hash senuelo.
            //
            // SE CUENTA LA LLAMADA Y NO SE MIDE EL TIEMPO, a proposito. Un
            // `assertThat(ms).isGreaterThan(30)` seria un flake esperando a
            // pasar: BCrypt tarda distinto en cada maquina, y en un runner
            // rapido el mismo codigo correcto fallaria. Y un test que falla sin
            // que nadie haya roto nada es peor que no tenerlo -- ensucia
            // justamente la senial que este CI viene a dar.
            //
            // Contar que `matches` se llamo igual prueba el MECANISMO, que es lo
            // que importa: el servicio verifica un hash aunque la cuenta no
            // exista. El costo en tiempo es consecuencia de eso.
            when(usuarios.findByEmail("nadie@local")).thenReturn(Optional.empty());

            mensajeDeFallo("nadie@local", PASSWORD);

            verify(codificador).matches(eq(PASSWORD), anyString());
        }

        @Test
        @DisplayName("al sexto intento fallido contra la misma cuenta corta el limitador")
        void rateLimitPorCuenta() {
            when(usuarios.findByEmail("franco@local"))
                    .thenReturn(Optional.of(usuarioGuardado("franco@local")));

            for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA; i++) {
                mensajeDeFallo("franco@local", "contrasena-equivocada-larga");
            }

            // El sexto ya no es "contrasena incorrecta": es "basta".
            assertThatThrownBy(() -> servicio.login(
                    new LoginRequest("franco@local", PASSWORD), IP))
                    .isNotInstanceOf(NoAutenticadoException.class);
        }

        @Test
        @DisplayName("un login exitoso limpia los intentos fallidos")
        void elExitoLimpiaElContador() {
            // Si no se limpiaran, cuatro errores de tipeo a lo largo del dia
            // dejarian la cuenta a un intento de bloquearse aunque en el medio
            // hayas entrado bien.
            when(usuarios.findByEmail("franco@local"))
                    .thenReturn(Optional.of(usuarioGuardado("franco@local")));

            for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA - 1; i++) {
                mensajeDeFallo("franco@local", "contrasena-equivocada-larga");
            }
            servicio.login(new LoginRequest("franco@local", PASSWORD), IP);

            // Despues del exito el contador arranca de cero, asi que otros
            // cuatro fallos siguen sin bloquear.
            for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA - 1; i++) {
                assertThat(mensajeDeFallo("franco@local", "contrasena-equivocada-larga"))
                        .isEqualTo("Email o contrasena incorrectos");
            }
        }
    }

    @Nested
    @DisplayName("Cerrar todas las sesiones")
    class CerrarSesiones {

        @Test
        @DisplayName("incrementa la token_version, que es lo que invalida los tokens viejos")
        void invalidaLosAnteriores() {
            Usuario franco = usuarioGuardado("franco@local");
            long antes = franco.getTokenVersion();
            when(usuarioActual.requerido()).thenReturn(franco);

            servicio.cerrarOtrasSesiones();

            assertThat(franco.getTokenVersion()).isGreaterThan(antes);
            verify(usuarios).save(franco);
        }
    }

    // ------------------------------------------------------------- utilidades

    /** Dispara un login que va a fallar y devuelve el mensaje, sin romper el test. */
    private String mensajeDeFallo(String email, String password) {
        try {
            servicio.login(new LoginRequest(email, password), IP);
            throw new AssertionError("se esperaba que el login fallara");
        } catch (NoAutenticadoException e) {
            return e.getMessage();
        }
    }

    private RegistroRequest registro(String nombre, String email) {
        return new RegistroRequest(nombre, email, PASSWORD, CODIGO);
    }

    private Grupo grupo() {
        Grupo g = new Grupo("Casa");
        escribirCampo(g, "id", GRUPO);
        return g;
    }

    private Usuario usuarioGuardado(String email) {
        Usuario u = new Usuario("Franco", email, HASH_DE_PASSWORD, GRUPO);
        escribirCampo(u, "id", "u-franco");
        return u;
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
