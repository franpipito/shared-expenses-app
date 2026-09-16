package com.gastoscompartidos.seguridad;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Corre `ValidacionDeConfiguracion` ANTES de que exista un solo bean.
 *
 * POR QUE EXISTE, que es lo unico interesante de esta clase.
 *
 * `ValidacionDeConfiguracion` era un `@Component` con `@Profile("produccion")` y
 * los chequeos en el constructor. Parecia suficiente, y no lo era: **nadie
 * depende de ese bean**, asi que Spring lo creaba cuando le tocaba. Y lo que le
 * tocaba antes era la cadena controlador -> servicio -> repositorio ->
 * `mongoTemplate`, que al no poder conectarse tiraba la excepcion primero.
 *
 * Verificado arrancando el jar con `SPRING_PROFILES_ACTIVE=produccion` y sin
 * secretos: la app moria con un `Connection refused` a localhost:27017 y **el
 * nombre `ValidacionDeConfiguracion` no aparecia una sola vez en el log**.
 *
 * Lo incomodo era cual chequeo quedaba muerto. Si `MONGO_URI` apunta a localhost
 * en produccion, Mongo no esta ahi -- asi que el driver timeoutea primero y el
 * chequeo escrito justamente para ese caso nunca llegaba a hablar. El guardian
 * solo funcionaba cuando la URI ya era correcta, que es cuando menos falta hace.
 *
 * POR QUE `ApplicationPreparedEvent` Y NO OTRO. Se publica al final de
 * `prepareContext()`: el `Environment` ya esta completo (properties, perfiles y
 * variables de entorno resueltas) y las definiciones de beans estan cargadas,
 * pero **`refresh()` todavia no corrio**, asi que no se instancio ningun
 * singleton. Es el ultimo momento en que se puede frenar el arranque sin que
 * nada se haya conectado a ningun lado.
 *
 * La alternativa era `ApplicationEnvironmentPreparedEvent`, que es mas temprano
 * pero tiene una trampa: `ConfigDataEnvironmentPostProcessor` -- el que carga
 * `application.properties` -- es el mismo un listener de ese evento, asi que
 * quedaria una carrera por el orden de los listeners y habria que implementar
 * `Ordered` para ganarla. `ApplicationPreparedEvent` no tiene esa ambiguedad.
 *
 * Se registra a mano en `BackendApplication.main()` con `addListeners`, porque
 * un listener que tiene que correr antes de los beans no puede ser un bean.
 */
public class ValidacionAlArrancar implements ApplicationListener<ApplicationPreparedEvent> {

    /** El perfil que distingue "esto es produccion" de "esto es la maquina de alguien". */
    private static final String PERFIL_PRODUCCION = "produccion";

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent evento) {
        Environment entorno = evento.getApplicationContext().getEnvironment();

        // El chequeo de perfil se hace a mano porque @Profile solo entiende de
        // beans, y este listener a proposito no lo es.
        if (!entorno.acceptsProfiles(perfiles -> perfiles.test(PERFIL_PRODUCCION))) {
            return;
        }

        // Construirlo ES validarlo: los chequeos viven en el constructor. Por eso
        // el objeto no se guarda en ningun lado -- si no tira, la app arranca.
        new ValidacionDeConfiguracion(
                requerida(entorno, "app.jwt.secreto"),
                requerida(entorno, "app.registro.codigo-invitacion"),
                requerida(entorno, "spring.mongodb.uri"));
    }

    /**
     * Las tres tienen default en `application.properties`, asi que en la practica
     * nunca faltan -- y si alguien los borrara, un null aca daria un
     * NullPointerException opaco en vez de decir que propiedad falta.
     */
    private static String requerida(Environment entorno, String clave) {
        String valor = entorno.getProperty(clave);
        if (valor == null) {
            throw new IllegalStateException(
                    "Falta la propiedad " + clave + ", que deberia tener un default en application.properties.");
        }
        return valor;
    }
}
