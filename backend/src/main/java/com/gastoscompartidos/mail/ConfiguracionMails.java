package com.gastoscompartidos.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                                    Clock reloj) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank() || remitente.isBlank()) {
            log.warn("Mails: no hay credenciales de Gmail, se escriben en el log. Ver docs/mails.md.");
            return new EnviadorPorLog();
        }
        log.info("Mails: por la API de Gmail, desde {}", remitente);
        return new EnviadorPorGmail(clientId, clientSecret, refreshToken, remitente, nombreRemitente, json, reloj);
    }
}
