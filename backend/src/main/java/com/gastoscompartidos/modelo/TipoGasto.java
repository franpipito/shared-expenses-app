package com.gastoscompartidos.modelo;

/**
 * Intencion del gasto, declarada por quien lo carga.
 *
 * No es derivable de los montos: un gasto COMPARTIDO donde el pagador se hace
 * cargo del 100% tiene los mismos numeros que uno PERSONAL, pero significa
 * otra cosa. Por eso se guarda explicito.
 */
public enum TipoGasto {
    /** Solo lo paga y lo consume quien lo cargo. No genera deuda. */
    PERSONAL,
    /** Se reparte entre los dos integrantes del grupo. */
    COMPARTIDO
}
