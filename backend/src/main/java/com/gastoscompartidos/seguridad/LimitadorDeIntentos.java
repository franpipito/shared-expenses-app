package com.gastoscompartidos.seguridad;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frena la fuerza bruta contra /auth/login y /auth/registro.
 *
 * Sin esto, el backend publico deja probar contrasenas sin limite. BCrypt tarda
 * ~100ms por intento, lo que ya estorba bastante, pero con concurrencia siguen
 * siendo miles de intentos por hora contra una cuenta.
 *
 * COMO SE ELIGE LA CLAVE. Se limita por email y NO solo por IP, a proposito:
 *
 *   - Solo por IP: un atacante con muchas IPs (una botnet, o simplemente IPv6)
 *     ataca una cuenta sin tocar nunca el limite.
 *   - Solo por email: un atacante desde una IP prueba de a poco contra miles de
 *     cuentas distintas.
 *
 * Se usan las dos, y el limite por email es el que de verdad protege: para
 * atacar la cuenta de Viole hay que mandar SU email, y eso no se puede falsear.
 * La IP, detras de un proxy, depende de un header que si se puede falsear.
 *
 * LIMITACIONES, para ser honestos:
 *   - Vive en memoria: se reinicia con cada deploy.
 *   - No funciona entre varias instancias.
 * Las dos dejan de valer si algun dia hay mas de un contenedor, y ahi la
 * solucion es mover los contadores a Redis o poner el limite en el borde. Para
 * una app de dos personas en una instancia, esto alcanza y no suma dependencias.
 */
@Component
public class LimitadorDeIntentos {

    /**
     * Limite para una cuenta concreta. Ajustado: cinco intentos fallidos contra
     * el mismo email son claramente un ataque, no un olvido.
     */
    public static final int MAX_POR_CUENTA = 5;

    /**
     * Limite para una IP. MUCHO mas alto, y por una razon concreta: Viole y
     * Franco entran desde el mismo wifi, asi que comparten IP. Con el mismo
     * limite que por cuenta, ella olvidandose la contrasena cinco veces dejaria
     * a el sin poder entrar. El limite por IP ataja el barrido masivo, no el
     * ataque dirigido: para eso esta el de cuenta.
     */
    public static final int MAX_POR_IP = 20;

    private static final Duration VENTANA = Duration.ofMinutes(15);

    /**
     * Tope de claves distintas en memoria. Sin esto, alguien que mande un millon
     * de emails inventados hace crecer el mapa sin freno: el limitador seria un
     * vector de denegacion de servicio en vez de una defensa.
     */
    private static final int MAX_CLAVES = 10_000;

    private final Map<String, Ventana> intentos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimitadorDeIntentos(Clock reloj) {
        this.reloj = reloj;
    }

    /** Se llama ANTES de verificar credenciales. */
    public void verificar(String clave, int maxIntentos) {
        Ventana ventana = intentos.get(clave);
        if (ventana == null || ventana.expiro(Instant.now(reloj))) {
            return;
        }
        if (ventana.fallos.get() >= maxIntentos) {
            throw new DemasiadosIntentosException(
                    "Demasiados intentos. Espera unos minutos y volve a probar.");
        }
    }

    /** Se llama cuando las credenciales resultaron incorrectas. */
    public void registrarFallo(String clave) {
        Instant ahora = Instant.now(reloj);
        if (intentos.size() > MAX_CLAVES) {
            intentos.entrySet().removeIf(e -> e.getValue().expiro(ahora));
        }
        intentos.compute(clave, (k, ventana) -> {
            if (ventana == null || ventana.expiro(ahora)) {
                return new Ventana(ahora);
            }
            ventana.fallos.incrementAndGet();
            return ventana;
        });
    }

    /** Se llama al entrar bien: un login exitoso limpia el historial. */
    public void limpiar(String clave) {
        intentos.remove(clave);
    }

    private final class Ventana {
        private final Instant inicio;
        private final AtomicInteger fallos = new AtomicInteger(1);

        private Ventana(Instant inicio) {
            this.inicio = inicio;
        }

        private boolean expiro(Instant ahora) {
            return inicio.plus(VENTANA).isBefore(ahora);
        }
    }
}
