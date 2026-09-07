package com.gastoscompartidos.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite y valida los JWT. Es el unico lugar del proyecto que sabe que existe un
 * token.
 *
 * QUE ES UN JWT, en concreto: tres partes separadas por puntos, en base64.
 *
 *   header.payload.firma
 *
 * El header dice con que algoritmo se firmo. El payload son los "claims", que
 * es informacion en texto plano -- **cualquiera puede leerla**, base64 no es
 * encriptacion. La firma es un HMAC del header y el payload usando una clave
 * secreta que solo tiene el servidor.
 *
 * Por eso un JWT no sirve para guardar secretos, pero si para garantizar que
 * nadie lo modifico: si alguien cambia un solo caracter del payload, la firma
 * deja de coincidir y la validacion falla. No hace falta guardar nada en el
 * servidor para verificarlo, y eso es lo que lo hace stateless.
 *
 * Consecuencia directa: **un token emitido no se puede revocar**. Vale hasta que
 * expire. Por eso la duracion es una decision de seguridad y no un detalle.
 */
@Component
public class ServicioDeTokens {

    private final SecretKey clave;
    private final Duration duracion;
    private final Clock reloj;

    public ServicioDeTokens(
            @Value("${app.jwt.secreto}") String secreto,
            @Value("${app.jwt.duracion-dias:30}") long duracionDias,
            Clock reloj) {

        // HMAC-SHA256 exige una clave de al menos 256 bits (32 bytes). Si es mas
        // corta, jjwt lanza excepcion al arrancar en vez de firmar con algo
        // debil, que es el comportamiento correcto: fallar fuerte y temprano.
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secreto tiene que tener al menos 32 caracteres; tiene " + bytes.length);
        }
        this.clave = io.jsonwebtoken.security.Keys.hmacShaKeyFor(bytes);
        this.duracion = Duration.ofDays(duracionDias);
        this.reloj = reloj;
    }

    /**
     * El token lleva el id del usuario y nada mas.
     *
     * Es a proposito: el JWT es un IDENTIFICADOR, no un portador de permisos.
     * Si le metieramos el nombre o el grupo adentro, esa copia quedaria vieja
     * apenas cambien y no habria forma de refrescarla hasta que expire el token.
     * Con solo el id, cada request lee el estado actual de la base.
     */
    public String emitirPara(Long usuarioId, long tokenVersion) {
        Instant ahora = Instant.now(reloj);
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                // La generacion de tokens del usuario. Es lo que permite
                // revocar: si en la base ese numero sube, este token queda
                // fuera aunque siga sin expirar y con la firma valida.
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    /**
     * Devuelve el id del usuario si el token es valido, o vacio si no lo es.
     *
     * Un token invalido no es un caso excepcional: es lo normal cuando expira, o
     * cuando alguien prueba con basura. Por eso devuelve Optional y no lanza:
     * quien llama decide que hacer, y el filtro simplemente no autentica.
     */
    public Optional<IdentidadDelToken> identidadDe(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)   // aca se chequea la firma
                    .clock(() -> Date.from(Instant.now(reloj)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();       // y aca ya se verifico tambien la expiracion

            // Un token viejo, emitido antes de que existiera el claim, se trata
            // como generacion 0.
            long tokenVersion = claims.get("tv", Number.class) == null
                    ? 0L
                    : claims.get("tv", Number.class).longValue();

            return Optional.of(new IdentidadDelToken(
                    Long.valueOf(claims.getSubject()), tokenVersion));
        } catch (JwtException | IllegalArgumentException e) {
            // Firma invalida, token expirado, malformado, algoritmo distinto al
            // esperado... todos terminan aca y todos significan lo mismo: no
            // autenticado.
            return Optional.empty();
        }
    }

    public Duration duracion() {
        return duracion;
    }
}
