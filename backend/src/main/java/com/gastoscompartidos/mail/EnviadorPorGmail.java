package com.gastoscompartidos.mail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Manda el mail por la API REST de Gmail, desde la cuenta de Gmail de Franco.
 *
 * POR QUE ESTO Y NO SMTP, que es lo que uno usaria primero: **Render gratis
 * bloquea la salida a los puertos SMTP** (25, 465 y 587) desde septiembre de
 * 2025. La API de Gmail va por HTTPS, puerto 443, como cualquier otra request.
 *
 * Y POR QUE NO UN PROVEEDOR COMO RESEND O BREVO: sin dominio propio, un mail
 * "de" una direccion @gmail.com mandado por un tercero no pasa la autenticacion
 * (DMARC) y termina en spam o rechazado. Mandado por la API de Gmail, sale de
 * los servidores de Google con la firma de gmail.com: llega a la bandeja. Gratis,
 * con un tope de unos 500 mails por dia que esta app no va a rozar.
 *
 * EL PRECIO ES OAUTH. Gmail no acepta usuario y contrasena: hay que autorizar una
 * vez con la cuenta (scope `gmail.send`, que solo permite MANDAR, no leer) y
 * guardar el refresh token que devuelve. Con el, en cada envio se pide un access
 * token de una hora. Los pasos para conseguirlo estan en `docs/mails.md`.
 *
 * No es un @Component: lo instancia `ConfiguracionMails` solo si estan las
 * credenciales, y si no, se usa `EnviadorPorLog`.
 */
public class EnviadorPorGmail implements EnviadorDeMails {

    private static final URI TOKEN = URI.create("https://oauth2.googleapis.com/token");
    private static final URI ENVIAR = URI.create(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send");

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String remitente;
    private final String nombreRemitente;
    private final JsonMapper json;
    private final Clock reloj;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String accessToken;
    private Instant accessTokenVence = Instant.EPOCH;

    public EnviadorPorGmail(String clientId, String clientSecret, String refreshToken,
                            String remitente, String nombreRemitente,
                            JsonMapper json, Clock reloj) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        String cuerpo = json.writeValueAsString(Map.of("raw", mimeEnBase64(para, asunto, texto)));
        HttpRequest pedido = HttpRequest.newBuilder(ENVIAR)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessTokenVigente())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();

        HttpResponse<String> respuesta = mandar(pedido);
        if (respuesta.statusCode() / 100 != 2) {
            throw new MailNoEnviadoException(
                    "Gmail rechazo el mail (" + respuesta.statusCode() + "): " + respuesta.body());
        }
    }

    /**
     * El access token dura una hora. Se reusa mientras le quede mas de un
     * minuto, para no pedir uno nuevo en cada mail.
     *
     * `synchronized`: dos reseteos a la vez podrian pedir dos tokens. No rompe
     * nada, pero no hay por que.
     */
    private synchronized String accessTokenVigente() {
        Instant ahora = Instant.now(reloj);
        if (accessToken != null && ahora.isBefore(accessTokenVence.minusSeconds(60))) {
            return accessToken;
        }

        String formulario = "grant_type=refresh_token"
                + "&client_id=" + codificar(clientId)
                + "&client_secret=" + codificar(clientSecret)
                + "&refresh_token=" + codificar(refreshToken);
        HttpRequest pedido = HttpRequest.newBuilder(TOKEN)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build();

        HttpResponse<String> respuesta = mandar(pedido);
        if (respuesta.statusCode() != 200) {
            // El caso tipico: el refresh token se revoco, o vencio porque el
            // proyecto de Google Cloud quedo en "Testing" (ahi duran 7 dias).
            throw new MailNoEnviadoException(
                    "Google no dio un access token (" + respuesta.statusCode() + "): " + respuesta.body());
        }
        JsonNode token = json.readTree(respuesta.body());
        accessToken = token.get("access_token").asString();
        accessTokenVence = ahora.plusSeconds(token.path("expires_in").asLong(3600));
        return accessToken;
    }

    /**
     * El mail en formato MIME, que es lo que la API espera en `raw`, en base64
     * "url-safe".
     *
     * Asunto y cuerpo llevan tildes, asi que van codificados: el asunto como
     * encoded-word (RFC 2047) y el cuerpo en base64 con charset UTF-8. Sin eso,
     * "contraseña" llega como "contraseÃ±a".
     */
    private String mimeEnBase64(String para, String asunto, String texto) {
        Base64.Encoder b64 = Base64.getEncoder();
        String mime = "From: " + encabezado(nombreRemitente) + " <" + remitente + ">\r\n"
                + "To: " + para + "\r\n"
                + "Subject: " + encabezado(asunto) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + b64.encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    private static String encabezado(String valor) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> mandar(HttpRequest pedido) {
        try {
            return http.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MailNoEnviadoException("No se pudo hablar con Google", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailNoEnviadoException("Se interrumpio el envio", e);
        }
    }
}
