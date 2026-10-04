package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agregar una categoria propia (seccion 2.5), sin ningun default (seccion 2.7).
 *
 * `icono` no se valida contra ninguna lista a proposito: desde la seccion 2.7
 * la app deja elegir un EMOJI libre con el teclado del sistema, asi que no hay
 * un catalogo cerrado contra el que validar. `IconoCategoria` (mobile) ya
 * sabe mostrar cualquier string -- un nombre de Lucide, para las categorias
 * historicas de Franco y Viole, o un emoji para todo lo demas.
 */
public record CrearCategoriaRequest(
        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 40, message = "el nombre no puede pasar de 40 caracteres")
        String nombre,

        @NotBlank(message = "el icono es obligatorio")
        String icono
) {
}
