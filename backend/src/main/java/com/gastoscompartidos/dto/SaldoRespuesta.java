package com.gastoscompartidos.dto;

import java.math.BigDecimal;

/**
 * La seccion PAREJA: quien le debe a quien, en el mes.
 *
 * Es del mes y no de toda la historia a proposito. Sin un concepto de
 * liquidacion ("ya te pague"), un saldo historico solo crece y a los pocos meses
 * es un numero grande que no significa nada real. Acotarlo al mes lo mantiene
 * chico y accionable, al costo de asumir que se arreglan mes a mes.
 *
 * Si algun dia quieren llevar la cuenta en serio, la solucion es una entidad
 * Liquidacion y saldo = deudas - pagos.
 *
 * @param monto     siempre positivo o cero. Cuanto se debe
 * @param deudorId  quien debe. null si estan a mano
 * @param acreedorId a quien le deben. null si estan a mano
 * @param aFavorMio positivo si me deben a mi, negativo si debo yo. Es el mismo
 *                  numero con signo, para que el cliente no tenga que comparar
 *                  ids para saber de que lado esta
 */
public record SaldoRespuesta(
        String mes,
        BigDecimal monto,
        Long deudorId,
        String deudorNombre,
        Long acreedorId,
        String acreedorNombre,
        BigDecimal aFavorMio
) {
}
