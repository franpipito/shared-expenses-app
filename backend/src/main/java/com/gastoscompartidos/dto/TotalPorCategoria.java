package com.gastoscompartidos.dto;

import java.math.BigDecimal;

/**
 * Cuanto puso una persona en una categoria durante el mes, y cuanto de eso fue
 * evitable.
 */
public record TotalPorCategoria(
        String categoriaId,
        String nombre,
        String icono,
        BigDecimal total,
        BigDecimal totalHormiga
) {
}
