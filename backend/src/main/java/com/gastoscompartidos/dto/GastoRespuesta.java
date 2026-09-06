package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.TipoGasto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un gasto, tal como lo ve la API.
 *
 * @param deudaGenerada monto - montoPagador. Es un campo derivado: no esta en la
 *                      tabla, lo calcula la entidad. Se manda igual para que el
 *                      cliente no tenga que hacer la resta y arriesgarse a
 *                      hacerla distinto que el backend.
 * @param version       la version de bloqueo optimista. El cliente la recibe y
 *                      la devuelve al editar, y asi el backend puede detectar
 *                      que otra persona toco el gasto en el medio.
 */
public record GastoRespuesta(
        Long id,
        BigDecimal monto,
        BigDecimal montoPagador,
        BigDecimal deudaGenerada,
        LocalDate fecha,
        String descripcion,
        TipoGasto tipo,
        boolean esHormiga,
        CategoriaRespuesta categoria,
        UsuarioRespuesta pagadoPor,
        Long version
) {

    /**
     * Se llama DENTRO de la transaccion, a proposito: aca se tocan
     * gasto.getCategoria() y gasto.getPagadoPor(), que son relaciones LAZY. Si
     * esto se ejecutara despues de cerrada la transaccion, seria una
     * LazyInitializationException.
     *
     * Por eso el mapeo a DTO vive en el servicio y no en el controlador.
     */
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
                CategoriaRespuesta.desde(gasto.getCategoria()),
                UsuarioRespuesta.desde(gasto.getPagadoPor()),
                gasto.getVersion()
        );
    }
}
