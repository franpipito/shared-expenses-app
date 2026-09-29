package com.gastoscompartidos.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Elige como salen los mails: por Gmail si estan las cuatro variables de
 * `docs/mails.md`, y si falta cualquiera, al log.
 *
 * Por que no fallar al arrancar si faltan, como hace `ValidacionDeConfiguracion`
 * con el secreto del JWT: la app funciona entera sin mails. Lo unico que se
 * pierde es el reseteo automatico, y el codigo igual queda en el log para
 * ayudar a mano. Frenar el deploy por eso seria desproporcionado. Lo que si se
 * hace es avisar fuerte en el log de arranque.
 *
 * PERO "avisar fuerte" antes era un solo `log.warn` al arrancar, sin importar
 * el perfil. La auditoria de seguridad de la v1.0 encontro el problema: si las
 * credenciales de Gmail se caen DESPUES de un tiempo en produccion (se
 * revocan a los 6 meses sin uso, o si cambia la contrasena de la cuenta de
 * Google -- ver `docs/mails.md`), el codigo de reseteo de seis digitos pasa a
 * quedar en texto plano en los logs de Render, y ese `warn` de arranque -- que
 * nadie vuelve a mirar -- es la unica senial. Podria durar meses sin que
 * nadie se entere.
 *
 * Ahora, con el perfil `produccion` activo, el mismo caso loguea en ERROR con
 * un mensaje que dice exactamente que esta pasando. Sigue sin frenar el
 * arranque -- eso seguiria siendo desproporcionado -- pero un ERROR en
 * produccion es la clase de linea que una alerta de logs si mira.
 */
@Configuration
public class ConfiguracionMails {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionMails.class);

    @Bean
    EnviadorDeMails enviadorDeMails(@Value("${app.mail.gmail.client-id:}") String clientId,
                                    @Value("${app.mail.gmail.client-secret:}") String clientSecret,
                                    @Value("${app.mail.gmail.refresh-token:}") String refreshToken,
                                    @Value("${app.mail.remitente:}") String remitente,
                                    @Value("${app.mail.nombre-remitente:MiNutria}") String nombreRemitente,
                                    JsonMapper json,
                                    Clock reloj,
                                    Environment entorno) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank() || remitente.isBlank()) {
            if (entorno.matchesProfiles("produccion")) {
                log.error("Mails: SIN credenciales de Gmail EN PRODUCCION. Los codigos de reseteo de "
                        + "contrasena van a quedar en texto plano en este log hasta que se configuren. "
                        + "Ver docs/mails.md.");
            } else {
                log.warn("Mails: no hay credenciales de Gmail, se escriben en el log. Ver docs/mails.md.");
            }
            return new EnviadorPorLog();
        }
        log.info("Mails: por la API de Gmail, desde {}", remitente);
        return new EnviadorPorGmail(clientId, clientSecret, refreshToken, remitente, nombreRemitente, json, reloj);
    }
}
