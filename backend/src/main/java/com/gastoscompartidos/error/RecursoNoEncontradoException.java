package com.gastoscompartidos.error;

/**
 * El recurso pedido no existe, o el usuario no tiene permiso para verlo.
 * Se traduce a HTTP 404.
 *
 * Que cubra los dos casos es deliberado. Si a un gasto personal ajeno le
 * respondieramos 403 ("existe pero no podes verlo"), estariamos confirmando que
 * existe -- y con eso se puede sondear la existencia de gastos que la otra
 * persona quiso mantener privados. Un 404 no dice nada.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
