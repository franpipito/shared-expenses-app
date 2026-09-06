package com.gastoscompartidos.error;

/**
 * Los datos son sintacticamente validos pero violan una regla del dominio.
 * Se traduce a HTTP 400.
 *
 * La diferencia con las validaciones del DTO: "monto tiene que ser positivo" se
 * puede chequear mirando solo el JSON, y eso lo resuelve Bean Validation. En
 * cambio "esa categoria no existe" o "ese gasto no es de tu grupo" requieren ir
 * a la base, asi que viven en el servicio y lanzan esta excepcion.
 */
public class ReglaDeNegocioException extends RuntimeException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
