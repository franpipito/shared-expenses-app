package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Sin @Email ni @Size aca, a proposito: en el login no queremos dar pistas sobre
 * el formato esperado ni distinguir "email mal escrito" de "email que no existe".
 * Todo lo que falle devuelve el mismo 401.
 */
public record LoginRequest(

        @NotBlank(message = "el email es obligatorio")
        String email,

        @NotBlank(message = "la contrasena es obligatoria")
        String password
) {
}
