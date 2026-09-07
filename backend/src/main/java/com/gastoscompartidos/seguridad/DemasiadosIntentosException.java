package com.gastoscompartidos.seguridad;

/**
 * Se superaron los intentos permitidos. Se traduce a HTTP 429 Too Many Requests.
 */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String mensaje) {
        super(mensaje);
    }
}
