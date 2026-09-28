package com.gastoscompartidos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ACA HABIA UN `codigoInvitacion`, y se saco para la v1.0 de la App Store: una
 * app publicada tiene que dejar registrarse a cualquiera que la baje. Lo que
 * cuidaba el codigo -- que un desconocido no se cree una cuenta en el grupo de
 * Viole y Franco -- ahora lo cuida otra cosa: cada registro crea su propio
 * grupo (ver AutenticacionServicio).
 *
 * Un cliente viejo que todavia lo mande no rompe nada: Jackson ignora los
 * campos que el record no declara.
 */
public record RegistroRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 100, message = "el nombre no puede pasar de 100 caracteres")
        String nombre,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        @Size(max = 255)
        String email,

        // El minimo de 8 es un piso razonable. El maximo NO es cosmetico: BCrypt
        // trunca silenciosamente despues de 72 bytes, asi que sin tope dos
        // contrasenas larguisimas que compartan los primeros 72 bytes serian
        // equivalentes.
        @NotBlank(message = "la contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "la contraseña tiene que tener entre 8 y 72 caracteres")
        String password
) {
}
