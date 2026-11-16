package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Crear o editar una categoria propia. Un solo record para el POST y el PUT,
 * igual que `GuardarGastoRequest`: los dos piden exactamente lo mismo, y dos
 * records identicos serian dos lugares donde cambiar una validacion.
 *
 * `icono` no se valida contra ninguna lista a proposito: desde la seccion 2.7
 * la app deja elegir un EMOJI libre con el teclado del sistema, asi que no hay
 * un catalogo cerrado contra el que validar. `IconoCategoria` (mobile) ya
 * sabe mostrar cualquier string -- un nombre de Lucide, para las categorias
 * historicas de Franco y Viole, o un emoji para todo lo demas.
 */
public record GuardarCategoriaRequest(
        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 40, message = "el nombre no puede pasar de 40 caracteres")
        String nombre,

        @NotBlank(message = "el icono es obligatorio")
        String icono
) {
}
