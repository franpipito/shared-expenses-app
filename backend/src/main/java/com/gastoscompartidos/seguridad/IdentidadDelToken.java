package com.gastoscompartidos.seguridad;

/**
 * Lo que viene adentro de un JWT valido.
 *
 * Es lo que {@link FiltroJwt} deja en el SecurityContext. Sigue siendo un
 * identificador y no un portador de permisos: no lleva nombre, ni grupo, ni
 * roles. Solo quien decis ser, y de que generacion de tokens venis.
 */
public record IdentidadDelToken(Long usuarioId, long tokenVersion) {
}
