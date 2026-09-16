package com.gastoscompartidos.seguridad;

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
 * NO ES UN BEAN, y eso es el arreglo de un bug real. Era un `@Component` con
 * `@Profile("produccion")`, pero **nadie dependia de el**: Spring lo creaba
 * cuando le tocaba, y lo que le tocaba antes era la cadena que termina en
 * `mongoTemplate`. Con la URI apuntando a una base que no existe -- que es
 * justo el caso que este chequeo viene a cazar -- el driver de Mongo tiraba
 * primero y esta clase no llegaba a hablar nunca.
 *
 * Ahora la invoca `ValidacionAlArrancar`, un listener que corre antes de que
 * exista un solo bean. Ahi esta contada la historia entera.
 *
 * Queda como una clase pura, sin Spring: recibe tres strings y tira o no tira.
 * Por eso `ValidacionDeConfiguracionTest` la puede ejercitar en milisegundos,
 * igual que `CalculadorDeAnimoTest` y `PeriodoTest`.
 */
public class ValidacionDeConfiguracion {

    public ValidacionDeConfiguracion(String secretoJwt, String codigoInvitacion, String mongoUri) {

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

        // La cadena que Atlas te da para copiar NO trae el nombre de la base:
        // termina en "mongodb.net/?retryWrites=true...". Si se pega tal cual, el
        // driver cae al default, que es `test`.
        //
        // Y entonces la app arranca perfecto contra una base vacia: el sembrador
        // crea las seis categorias ahi, el registro funciona, todo "anda". El
        // problema aparece el dia que alguien busca los datos en `gastos` y no
        // hay nada, o cuando se cambia la URI y los datos "desaparecen".
        //
        // Es exactamente la clase de fallo silencioso contra la que Mongo no
        // tiene defensas propias: sin esquema, una base equivocada es
        // indistinguible de una base nueva.
        if (sinNombreDeBase(mongoUri)) {
            throw new IllegalStateException("""
                    MONGO_URI no dice contra que base conectarse, asi que el driver usaria `test`.
                    La cadena que da Atlas no lo incluye: hay que agregarlo a mano entre el host
                    y el signo de pregunta.
                        mal:  mongodb+srv://usuario:clave@cluster.mongodb.net/?retryWrites=true
                        bien: mongodb+srv://usuario:clave@cluster.mongodb.net/gastos?retryWrites=true""");
        }
    }

    /**
     * Si la URI trae un nombre de base entre el host y los parametros.
     *
     * Se busca el ULTIMO `@` y no el primero: la contrasena puede tener uno
     * adentro, y el host nunca. A partir de ahi se corta en el `?` para no
     * confundir una barra de los parametros con la de la base.
     */
    private static boolean sinNombreDeBase(String uri) {
        int finDeCredenciales = uri.lastIndexOf('@');
        String hostYResto = (finDeCredenciales >= 0) ? uri.substring(finDeCredenciales + 1) : uri;

        int inicioDeParametros = hostYResto.indexOf('?');
        if (inicioDeParametros >= 0) {
            hostYResto = hostYResto.substring(0, inicioDeParametros);
        }

        int barra = hostYResto.indexOf('/');
        // Sin barra no hay base; con la barra al final ("host/") tampoco.
        return barra < 0 || barra == hostYResto.length() - 1;
    }
}
