package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.TipoGasto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Un gasto, tal como lo ve la API.
 *
 * @param deudaGenerada monto - montoPagador. Es un campo derivado: no esta en el
 *                      documento, lo calcula la entidad. Se manda igual para que
 *                      el cliente no tenga que hacer la resta y arriesgarse a
 *                      hacerla distinto que el backend.
 * @param porcentajePagador que porcentaje del gasto le toca a quien pago, ya
 *                          redondeado a entero. Es OTRO campo derivado, y esta
 *                          por el mismo motivo que `deudaGenerada`: la pantalla
 *                          de edicion necesita saber que reparto tenia el gasto
 *                          para preseleccionar el chip, y la unica forma de
 *                          calcularlo en el cliente seria dividir montoPagador
 *                          por monto -- o sea, hacer aritmetica de plata en
 *                          JavaScript, con los montos ya convertidos a punto
 *                          flotante. Justo lo que BigDecimal y Decimal128 vienen
 *                          evitando de punta a punta.
 *
 *                          Null en los PERSONAL, donde no hay reparto que mostrar.
 * @param version       la version de bloqueo optimista. El cliente la recibe y
 *                      la devuelve al editar, y asi el backend puede detectar
 *                      que otra persona toco el gasto en el medio.
 */
public record GastoRespuesta(
        String id,
        BigDecimal monto,
        BigDecimal montoPagador,
        BigDecimal deudaGenerada,
        LocalDate fecha,
        String descripcion,
        TipoGasto tipo,
        boolean esHormiga,
        CategoriaRespuesta categoria,
        UsuarioRespuesta pagadoPor,
        Integer porcentajePagador,
        Long version,
        /** El pozo del que salio, o null si es un gasto de la vida normal. */
        String pozoId
) {
    /**
     * ESTE METODO SE SIMPLIFICO MUCHO AL PASAR A MONGO, y vale saber por que.
     *
     * Con JPA habia que llamarlo DENTRO de la transaccion, porque tocaba
     * `gasto.getCategoria()` y `gasto.getPagadoPor()`, que eran relaciones LAZY:
     * ejecutarlo con la sesion cerrada era una LazyInitializationException. Por
     * eso el mapeo a DTO tenia que vivir en el servicio y no en el controlador,
     * y por eso las consultas necesitaban `join fetch`.
     *
     * Aca la categoria y el pagador son documentos EMBEBIDOS: ya vinieron en la
     * misma lectura que el gasto. No hay carga diferida, no hay sesion que
     * pueda estar cerrada, no hay N+1 posible. El objeto que se lee esta
     * completo desde el momento en que existe.
     *
     * Es probablemente la ventaja mas concreta que trajo el modelo de documentos
     * a esta app —- y la contracara del costo que se paga en ReferenciaUsuario.
     */
    /**
     * `montoPagador / monto * 100`, redondeado.
     *
     * La division va con escala y RoundingMode explicitos porque BigDecimal lo
     * exige: sin eso, una division que no da exacta (los $10,01 al 50/50 de
     * siempre) tira ArithmeticException en vez de redondear.
     *
     * El redondeo se hace ACA y no en el cliente a proposito. Que el numero
     * salga del backend significa que las dos apps -- mobile y la web que venga
     * -- preseleccionan el mismo chip, igual que muestran la misma nutria.
     */
    private static Integer porcentajePagador(Gasto gasto) {
        if (gasto.getTipo() == TipoGasto.PERSONAL) return null;
        if (gasto.getMonto().signum() == 0) return null;

        return gasto.getMontoPagador()
                .multiply(BigDecimal.valueOf(100))
                .divide(gasto.getMonto(), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public static GastoRespuesta desde(Gasto gasto) {
        return new GastoRespuesta(
                gasto.getId(),
                gasto.getMonto(),
                gasto.getMontoPagador(),
                gasto.deudaGenerada(),
                gasto.getFecha(),
                gasto.getDescripcion(),
                gasto.getTipo(),
                gasto.esHormiga(),
                new CategoriaRespuesta(
                        gasto.getCategoria().categoriaId(),
                        gasto.getCategoria().nombre(),
                        gasto.getCategoria().icono()),
                new UsuarioRespuesta(
                        gasto.getPagadoPor().usuarioId(),
                        gasto.getPagadoPor().nombre()),
                porcentajePagador(gasto),
                gasto.getVersion(),
                gasto.getPozoId()
        );
    }
}
