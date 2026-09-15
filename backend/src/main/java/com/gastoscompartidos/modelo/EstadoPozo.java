package com.gastoscompartidos.modelo;

/**
 * Estado de un pozo. Solo puede haber UN pozo ABIERTO por grupo.
 *
 * Esa restriccion no es cosmetica: es lo que permite que el formulario de alta
 * no tenga que preguntar "a que pozo", que en la pantalla mas rapida de la app
 * seria un campo mas. Ver docs/vaquita.md.
 */
public enum EstadoPozo {
    /** Acepta aportes y gastos. */
    ABIERTO,
    /** El viaje termino. No acepta nada mas, pero se puede seguir consultando. */
    CERRADO
}
