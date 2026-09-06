package com.gastoscompartidos.modelo;

/**
 * El estado de animo de la nutria, que es la cara visible de como viene el mes.
 *
 * Sale de comparar el gasto hormiga del tramo transcurrido del mes contra el
 * MISMO tramo del mes anterior. Es una medida relativa a proposito: la usuaria
 * es freelance y sus ingresos son irregulares, asi que cualquier umbral en pesos
 * seria injusto en un mes flojo y demasiado permisivo en uno bueno.
 *
 * Y es una comparacion contra el pasado y no contra una meta porque lo que ella
 * dijo que queria es "replantearme porque los hice e intentar reducirlos". Su
 * objetivo es bajar, no estar debajo de una linea. La nutria le responde a eso.
 */
public enum AnimoNutria {

    /** El gasto hormiga bajo respecto del mes anterior, o directamente no hubo. */
    CONTENTA,

    /** Se mantiene parecido, o todavia no hay con que comparar. */
    TRANQUILA,

    /** El gasto hormiga subio respecto del mes anterior. */
    PREOCUPADA
}
