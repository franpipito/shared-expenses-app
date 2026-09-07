package com.gastoscompartidos.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impide que la app arranque en produccion con los valores de desarrollo.
 *
 * El escenario que evita es concreto: `application.properties` trae defaults
 * comodos para trabajar en local, y estan **a la vista en un repo publico**.
 * Quien lea el secreto del JWT puede firmar tokens validos para cualquier
 * usuario; quien lea el codigo de invitacion puede registrarse.
 *
 * Si esos defaults llegaran a produccion, la app funcionaria perfecto y estaria
 * completamente abierta, sin ninguna senial de que algo anda mal. Fallar al
 * arrancar es mucho mejor que arrancar insegura y en silencio.
 *
 * @Profile("produccion") hace que este bean exista SOLO cuando ese perfil esta
 * activo. Los perfiles son la forma de Spring de tener configuracion distinta
 * por entorno; se activan con la variable SPRING_PROFILES_ACTIVE.
 */
@Component
@Profile("produccion")
public class ValidacionDeConfiguracion {

    public ValidacionDeConfiguracion(
            @Value("${app.jwt.secreto}") String secretoJwt,
            @Value("${app.registro.codigo-invitacion}") String codigoInvitacion,
            @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto) {

        if (secretoJwt.contains("NO-USAR-EN-PRODUCCION")) {
            throw new IllegalStateException("""
                    Falta la variable de entorno JWT_SECRETO.
                    La app esta usando el secreto de desarrollo, que esta publicado en el repo:
                    cualquiera podria firmar tokens validos para cualquier usuario.
                    Genera uno con: openssl rand -base64 48""");
        }

        if ("nutrias".equals(codigoInvitacion)) {
            throw new IllegalStateException("""
                    Falta la variable de entorno CODIGO_INVITACION.
                    El codigo de desarrollo esta en el repo, asi que cualquiera podria registrarse.""");
        }

        // update en produccion modificaria el esquema por su cuenta, saltandose
        // Flyway y dejando la base y las migraciones fuera de sincronia.
        if (!"validate".equals(ddlAuto) && !"none".equals(ddlAuto)) {
            throw new IllegalStateException(
                    "ddl-auto tiene que ser validate o none en produccion, no '" + ddlAuto + "'. "
                    + "El esquema lo maneja Flyway.");
        }
    }
}
