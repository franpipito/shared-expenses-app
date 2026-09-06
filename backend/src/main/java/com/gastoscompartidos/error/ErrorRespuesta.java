package com.gastoscompartidos.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Formato unico de error de toda la API, para que los clientes no tengan que
 * adivinar la forma segun el endpoint.
 *
 * `record` es de Java 16+: genera constructor, accesores, equals, hashCode y
 * toString a partir de los componentes. Es inmutable por definicion. Para DTOs
 * es exactamente lo que uno quiere y ahorra 40 lineas de boilerplate.
 *
 * @param mensaje descripcion legible del problema
 * @param errores detalle campo -> error, solo en fallos de validacion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorRespuesta(String mensaje, Map<String, String> errores) {

    public static ErrorRespuesta de(String mensaje) {
        return new ErrorRespuesta(mensaje, null);
    }
}
