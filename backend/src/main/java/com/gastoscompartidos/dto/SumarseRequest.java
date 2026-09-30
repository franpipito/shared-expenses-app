package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;

public record SumarseRequest(
        @NotBlank(message = "hace falta el código de invitación")
        String codigo
) {
}
