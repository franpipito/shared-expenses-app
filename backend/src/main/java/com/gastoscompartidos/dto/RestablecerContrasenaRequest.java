package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param codigo   los seis digitos que llegaron por mail
 * @param password la contrasena nueva. Pasa por la misma politica que el
 *                 registro (PoliticaDeContrasenas), no por una mas laxa.
 */
public record RestablecerContrasenaRequest(
        @NotBlank(message = "el email es obligatorio")
        String email,

        @NotBlank(message = "falta el código")
        String codigo,

        @NotBlank(message = "la contraseña es obligatoria")
        @Size(max = 72, message = "la contraseña no puede pasar de 72 caracteres")
        String password
) {
}
