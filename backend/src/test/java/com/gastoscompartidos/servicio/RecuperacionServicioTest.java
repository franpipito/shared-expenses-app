package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.OlvideContrasenaRequest;
import com.gastoscompartidos.dto.RestablecerContrasenaRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.mail.EnviadorDeMails;
import com.gastoscompartidos.mail.MailNoEnviadoException;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.DemasiadosIntentosException;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.ServicioDeTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del "me olvide la contrasena".
 *
 * Dos falsos hechos a mano, y vale saber por que no son mocks de Mockito:
 *  - El **enviador de mails** se queda con cada mail "mandado". Asi el test lee
 *    el codigo del mail, igual que la persona, en vez de espiar el que se genero.
 *  - El **reloj** se puede adelantar: es la unica forma de probar que el codigo
 *    vence sin esperar 15 minutos de verdad.
 *
 * El encoder es BCrypt real, como en los otros tests de auth: un servicio que
 * guardara el codigo en claro tiene que fallar aca.
 */
class RecuperacionServicioTest {

    private static final String IP = "127.0.0.1";
    private static final String EMAIL = "viole@local";
    private static final String NUEVA = "una frase larga nueva para entrar";

    private UsuarioRepositorio usuarios;
    private MailsGuardados mails;
    private RelojQueAvanza reloj;
    private RecuperacionServicio servicio;
    private PasswordEncoder bcrypt;
    private Usuario viole;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        ServicioDeTokens tokens = mock(ServicioDeTokens.class);
        when(tokens.emitirPara(anyString(), anyLong())).thenReturn("un.token.nuevo");
        when(tokens.duracion()).thenReturn(Duration.ofDays(30));

        mails = new MailsGuardados();
        reloj = new RelojQueAvanza(Instant.parse("2026-09-29T12:00:00Z"));
        bcrypt = new BCryptPasswordEncoder();

        viole = new Usuario("Viole", EMAIL, bcrypt.encode("la contrasena vieja de siempre"), "grupo-1");
        escribirCampo(viole, "id", "u-viole");
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(viole));
        when(usuarios.findByEmail("nadie@local")).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio = new RecuperacionServicio(usuarios, bcrypt, mails,
                new LimitadorDeIntentos(reloj), tokens, reloj);
    }

    @Test
    @DisplayName("un email sin cuenta no da error ni manda nada: no revela quién tiene cuenta")
    void emailInexistente() {
        servicio.pedirCodigo(new OlvideContrasenaRequest("nadie@local"), IP);

        assertThat(mails.mandados).isEmpty();
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("el código llega por mail, de seis dígitos, y se guarda hasheado")
    void mandaElCodigo() {
        servicio.pedirCodigo(new OlvideContrasenaRequest("  VIOLE@local "), IP);

        String codigo = mails.ultimoCodigo();
        assertThat(codigo).matches("\\d{6}");
        assertThat(mails.mandados.get(0).para).isEqualTo(EMAIL);
        // Nunca en claro en la base.
        assertThat(viole.getResetCodigoHash()).isNotEqualTo(codigo).startsWith("$2");
        assertThat(viole.getResetVence()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("con el código correcto cambia la contraseña, cierra las otras sesiones y deja adentro")
    void restableceBien() {
        long versionAntes = viole.getTokenVersion();
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);

        TokenRespuesta r = servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, mails.ultimoCodigo(), NUEVA));

        assertThat(r.token()).isEqualTo("un.token.nuevo");
        assertThat(bcrypt.matches(NUEVA, viole.getPasswordHash())).isTrue();
        assertThat(viole.getTokenVersion()).isGreaterThan(versionAntes);
        assertThat(viole.getResetCodigoHash()).isNull();
    }

    @Test
    @DisplayName("el mismo código no sirve dos veces")
    void unSoloUso() {
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        String codigo = mails.ultimoCodigo();
        servicio.restablecer(new RestablecerContrasenaRequest(EMAIL, codigo, NUEVA));

        assertThatThrownBy(() -> servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, codigo, "otra frase larga distinta")))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("vencido a los 15 minutos, aunque sea el correcto")
    void vence() {
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        reloj.avanzar(Duration.ofMinutes(16));

        assertThatThrownBy(() -> servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, mails.ultimoCodigo(), NUEVA)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("venció");
    }

    @Test
    @DisplayName("después de 5 errores ni el código correcto sirve: adivinar no paga")
    void cincoIntentos() {
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        String correcto = mails.ultimoCodigo();
        String equivocado = correcto.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < RecuperacionServicio.MAX_INTENTOS; i++) {
            assertThatThrownBy(() -> servicio.restablecer(
                    new RestablecerContrasenaRequest(EMAIL, equivocado, NUEVA)))
                    .isInstanceOf(ReglaDeNegocioException.class);
        }
        assertThatThrownBy(() -> servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, correcto, NUEVA)))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("email sin cuenta y código equivocado dan el mismo mensaje")
    void mismoMensaje() {
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);

        String sinCuenta = mensajeDe(() -> servicio.restablecer(
                new RestablecerContrasenaRequest("nadie@local", "123456", NUEVA)));
        String equivocado = mensajeDe(() -> servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, "no-es", NUEVA)));

        assertThat(sinCuenta).isEqualTo(equivocado);
    }

    @Test
    @DisplayName("una contraseña que no pasa la política no gasta el código")
    void politicaNoGastaElCodigo() {
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        String codigo = mails.ultimoCodigo();

        assertThatThrownBy(() -> servicio.restablecer(
                new RestablecerContrasenaRequest(EMAIL, codigo, "corta")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("12");
        assertThat(viole.getResetIntentos()).isZero();

        // Y el mismo codigo sigue sirviendo con una buena.
        servicio.restablecer(new RestablecerContrasenaRequest(EMAIL, codigo, NUEVA));
        assertThat(bcrypt.matches(NUEVA, viole.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("al sexto pedido para el mismo email corta: no se le inunda la casilla a nadie")
    void topeDePedidos() {
        for (int i = 0; i < LimitadorDeIntentos.MAX_POR_CUENTA; i++) {
            servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        }
        assertThatThrownBy(() -> servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("si el mail no sale, el pedido no falla: un error distinto revelaría que la cuenta existe")
    void mailQueNoSale() {
        mails.fallar = true;
        servicio.pedirCodigo(new OlvideContrasenaRequest(EMAIL), IP);
        // Llego hasta aca sin excepcion.
        assertThat(viole.getResetCodigoHash()).isNotNull();
    }

    // ------------------------------------------------------------- utilidades

    private static String mensajeDe(Runnable r) {
        try {
            r.run();
            throw new AssertionError("se esperaba una excepcion");
        } catch (ReglaDeNegocioException e) {
            return e.getMessage();
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

    /** Un enviador que guarda lo que "manda", para leer el codigo como lo leeria la persona. */
    private static final class MailsGuardados implements EnviadorDeMails {
        record Mail(String para, String asunto, String texto) { }

        final List<Mail> mandados = new ArrayList<>();
        boolean fallar;

        @Override
        public void enviar(String para, String asunto, String texto) {
            if (fallar) throw new MailNoEnviadoException("Gmail no contesta");
            mandados.add(new Mail(para, asunto, texto));
        }

        String ultimoCodigo() {
            Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(mandados.get(mandados.size() - 1).texto);
            assertThat(m.find()).as("el mail trae un codigo de seis digitos").isTrue();
            return m.group(1);
        }
    }

    /** Un reloj que el test puede adelantar. */
    private static final class RelojQueAvanza extends Clock {
        private Instant ahora;

        RelojQueAvanza(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
