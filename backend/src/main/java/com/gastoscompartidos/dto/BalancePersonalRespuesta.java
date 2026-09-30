package com.gastoscompartidos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Mi Plata" (sección 2.3b): {@code restante = ingresado - gastado}, mismo
 * invariante que {@link PozoRespuesta}, pero para una sola persona y sin
 * corte de mes.
 *
 * @param restante puede ser NEGATIVO, y no es un error -- mismo criterio que
 *                 el {@code restante} de la vaquita: validar no es lo mismo
 *                 que bloquear.
 */
public record BalancePersonalRespuesta(
        BigDecimal ingresado,
        BigDecimal gastado,
        BigDecimal restante,
        List<IngresoRespuesta> ingresos
) {
}
