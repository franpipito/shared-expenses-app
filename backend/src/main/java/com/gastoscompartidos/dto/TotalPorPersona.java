package com.gastoscompartidos.dto;

import java.math.BigDecimal;

/**
 * Cuanto puso cada uno en el pozo: "Franco $400.000 / Viole $400.000".
 *
 * Lo calcula el backend y no el cliente, aunque la suma sea trivial, por la
 * misma razon que todo el resto de la aritmetica de plata: en JavaScript los
 * montos llegan como numeros de punto flotante, y sumar ahi reintroduce
 * exactamente el problema del centavo que BigDecimal y Decimal128 vienen
 * evitando de punta a punta.
 */
public record TotalPorPersona(String usuarioId, String nombre, BigDecimal total) {
}
