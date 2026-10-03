package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agregar una categoria propia (seccion 2.5).
 *
 * `icono` no se valida contra una lista fija de nombres de Lucide a
 * proposito: acoplar el backend al catalogo de iconos de la app mobile es
 * mas maquinaria que regla, y `IconoCategoria` ya tiene un fallback (el de
 * "otros") para cualquier nombre que no reconozca. La app solo deja elegir
 * entre un puñado curado, asi que en la practica siempre llega uno valido.
 */
public record CrearCategoriaRequest(
        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 40, message = "el nombre no puede pasar de 40 caracteres")
        String nombre,

        @NotBlank(message = "el icono es obligatorio")
        String icono
) {
}
