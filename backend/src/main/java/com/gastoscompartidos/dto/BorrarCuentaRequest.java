package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param password la contrasena actual. El token solo no alcanza para algo que
 *                 no se puede deshacer: dura 30 dias y vive en el telefono, asi
 *                 que un telefono desbloqueado en otras manos bastaria para
 *                 borrar la cuenta.
 */
public record BorrarCuentaRequest(
        @NotBlank(message = "hace falta tu contraseña para confirmar")
        String password
) {
}
