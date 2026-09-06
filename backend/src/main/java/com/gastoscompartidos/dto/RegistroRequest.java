package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param codigoInvitacion el backend va a estar publico en Railway. Sin este
 *                         codigo, cualquiera que encuentre la URL se crearia una
 *                         cuenta. Sale de una variable de entorno, asi que se
 *                         puede rotar sin tocar codigo.
 */
public record RegistroRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 100, message = "el nombre no puede pasar de 100 caracteres")
        String nombre,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato valido")
        @Size(max = 255)
        String email,

        // El minimo de 8 es un piso razonable. El maximo NO es cosmetico: BCrypt
        // trunca silenciosamente despues de 72 bytes, asi que sin tope dos
        // contrasenas larguisimas que compartan los primeros 72 bytes serian
        // equivalentes.
        @NotBlank(message = "la contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "la contrasena tiene que tener entre 8 y 72 caracteres")
        String password,

        @NotBlank(message = "hace falta el codigo de invitacion")
        String codigoInvitacion
) {
}
