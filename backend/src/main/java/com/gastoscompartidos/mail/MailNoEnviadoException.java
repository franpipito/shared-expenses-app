package com.gastoscompartidos.mail;

/** El proveedor de mail no acepto el mensaje (credenciales, red, cuota). */
public class MailNoEnviadoException extends RuntimeException {

    public MailNoEnviadoException(String mensaje) {
        super(mensaje);
    }

    public MailNoEnviadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
