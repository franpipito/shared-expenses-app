package com.gastoscompartidos.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param password la contrasena actual. Igual que en BorrarCuentaRequest: el
 *                 token solo no alcanza para confirmar algo que no se puede
 *                 deshacer sin la otra persona (volver a juntar los grupos
 *                 necesita un codigo de invitacion nuevo).
 */
public record SalirDelGrupoRequest(
        @NotBlank(message = "hace falta tu contraseña para confirmar")
        String password
) {
}
