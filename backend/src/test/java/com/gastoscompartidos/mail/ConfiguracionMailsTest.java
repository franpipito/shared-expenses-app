package com.gastoscompartidos.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que EnviadorDeMails elija bien segun las credenciales, y que el perfil
 * "produccion" no cambie ESA eleccion -- solo la severidad con la que se
 * loguea, que no se prueba aca (probar el nivel de un log pediria capturar el
 * appender de Logback, una maquinaria nueva para una linea que se revisa
 * leyendo el codigo). Ver el javadoc de la clase para el motivo del cambio.
 *
 * `MockEnvironment` es de `spring-test`, no hace falta un contexto de Spring
 * para usarlo: es una clase mas, se instancia con `new` como cualquier otra.
 */
class ConfiguracionMailsTest {

    private final ConfiguracionMails config = new ConfiguracionMails();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock reloj = Clock.systemUTC();

    @Test
    void sinCredencialesYSinPerfilProduccionEscribeAlLog() {
        MockEnvironment entorno = new MockEnvironment();

        EnviadorDeMails enviador = config.enviadorDeMails("", "", "", "", "MiNutria", json, reloj, entorno);

        assertThat(enviador).isInstanceOf(EnviadorPorLog.class);
    }

    @Test
    void sinCredencialesYCONPerfilProduccionTambienEscribeAlLog() {
        MockEnvironment entorno = new MockEnvironment();
        entorno.addActiveProfile("produccion");

        EnviadorDeMails enviador = config.enviadorDeMails("", "", "", "", "MiNutria", json, reloj, entorno);

        // El perfil no cambia QUE se devuelve, solo la severidad del log (ver
        // ConfiguracionMails). Que EnviadorPorLog siga siendo el resultado en
        // produccion es a proposito: fallar el arranque por esto seria
        // desproporcionado, es la app entera la que no levantaria.
        assertThat(enviador).isInstanceOf(EnviadorPorLog.class);
    }

    @Test
    void unaSolaCredencialFaltanteYaCaeAlLog() {
        MockEnvironment entorno = new MockEnvironment();

        EnviadorDeMails enviador = config.enviadorDeMails(
                "id", "secreto", "refresh", /* remitente */ "", "MiNutria", json, reloj, entorno);

        assertThat(enviador).isInstanceOf(EnviadorPorLog.class);
    }

    @Test
    void conLasCuatroCredencialesUsaGmailSinImportarElPerfil() {
        MockEnvironment entorno = new MockEnvironment();
        entorno.addActiveProfile("produccion");

        EnviadorDeMails enviador = config.enviadorDeMails(
                "id", "secreto", "refresh", "minutria@gmail.com", "MiNutria", json, reloj, entorno);

        assertThat(enviador).isInstanceOf(EnviadorPorGmail.class);
    }
}
