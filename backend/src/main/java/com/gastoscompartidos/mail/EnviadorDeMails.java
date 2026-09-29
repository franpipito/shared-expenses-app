package com.gastoscompartidos.mail;

/**
 * Mandar un mail de texto. Hoy lo usa solo el reseteo de contrasena.
 *
 * Es una interfaz por el mismo motivo que `UsuarioActual`: el servicio no tiene
 * que saber COMO sale el mail. En local lo "manda" `EnviadorPorLog`, que lo
 * escribe en el log; en produccion, `EnviadorPorGmail`, por la API de Gmail. Y
 * en los tests, un falso que se queda con el mail para mirar que codigo trae.
 *
 * Cual se usa lo decide `ConfiguracionMails`, segun haya o no credenciales de
 * Gmail.
 */
public interface EnviadorDeMails {

    /**
     * @throws MailNoEnviadoException si el proveedor no lo acepto. El que llama
     *         decide que hacer: el reseteo lo loguea y sigue, para no revelar
     *         si el email existe.
     */
    void enviar(String para, String asunto, String texto);
}
