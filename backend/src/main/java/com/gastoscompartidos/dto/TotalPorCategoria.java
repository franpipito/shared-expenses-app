package com.gastoscompartidos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuanto puso una persona en una categoria durante el mes, y cuanto de eso fue
 * evitable.
 *
 * Una fila puede juntar VARIAS categorias de la base que se llaman igual (ver
 * `ResumenServicio.agruparPorCategoria`): `categoriaIds` son todas, y es lo que
 * la app usa para abrir el detalle de la fila. `categoriaId` es una sola de
 * ellas, y queda para que una app vieja, que la usa como clave de la fila, siga
 * andando contra este backend.
 */
public record TotalPorCategoria(
        String categoriaId,
        List<String> categoriaIds,
        String nombre,
        String icono,
        BigDecimal total,
        BigDecimal totalHormiga
) {
}
