package com.gastoscompartidos.seguridad;

/**
 * No se pudo determinar quien hace la request. Se traduce a HTTP 401.
 *
 * Extiende RuntimeException (no Exception) para que sea "unchecked": no hay que
 * declararla con `throws` en cada metodo que la pueda propagar. En Spring es la
 * convencion para errores que se manejan de forma centralizada.
 */
public class NoAutenticadoException extends RuntimeException {

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
