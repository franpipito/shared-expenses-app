package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;

public record OlvideContrasenaRequest(
        @NotBlank(message = "el email es obligatorio")
        String email
) {
}
