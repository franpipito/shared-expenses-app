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
            @Value("${spring.mongodb.uri}") String mongoUri) {

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

        // ACA HABIA UN CHEQUEO DE ddl-auto, que con Postgres impedia que
        // Hibernate modificara el esquema por su cuenta saltandose Flyway.
        // Mongo no tiene esquema, asi que ese chequeo perdio sentido -- pero el
        // hueco se puede llenar con algo que si aplica.
        //
        // Si la app de produccion arranca con la URI local, levanta contra una
        // base vacia y "funciona": deja registrarse, deja cargar gastos, y nadie
        // se entera de que los datos se van a un Mongo efimero adentro del
        // contenedor. Es peor que fallar, porque falla en silencio.
        if (mongoUri.contains("localhost") || mongoUri.contains("127.0.0.1")) {
            throw new IllegalStateException("""
                    Falta la variable de entorno MONGO_URI.
                    La app esta apuntando a la base local, no a Atlas: arrancaria contra una
                    base vacia sin dar ningun error, y los datos se perderian al reiniciar.""");
        }

        // Las credenciales de Atlas viajan adentro de la URI. Que no aparezcan
        // las de desarrollo, que estan en el repo a la vista.
        if (mongoUri.contains("gastos_local")) {
            throw new IllegalStateException(
                    "MONGO_URI trae la contrasena de desarrollo, que esta publicada en el repo.");
        }
    }
}
