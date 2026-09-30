package com.gastoscompartidos.dto;

import java.math.BigDecimal;

/**
 * La seccion PAREJA, pero de toda la historia: {@code deudas - pagos}. Ver
 * {@link SaldoRespuesta}, que es del mes y sigue existiendo sin cambios.
 *
 * @param monto      siempre positivo o cero. Cuanto se debe
 * @param deudorId   quien debe. null si estan a mano
 * @param acreedorId a quien le deben. null si estan a mano
 * @param aFavorMio  positivo si me deben a mi, negativo si debo yo
 */
public record SaldoTotalRespuesta(
        BigDecimal monto,
        String deudorId,
        String deudorNombre,
        String acreedorId,
        String acreedorNombre,
        BigDecimal aFavorMio
) {
}
