package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.AnimoNutria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La seccion PERSONAL: cuanto gaste yo este mes.
 *
 * Todos los montos son "mi parte", no el total del grupo: de un gasto compartido
 * de $1.000 al 50/50 aparecen $500, haya pagado quien haya pagado. Es lo que
 * consumi, que es la pregunta que se hace la usuaria.
 *
 * @param totalHormiga            el numero que ella pidio ver primero
 * @param totalHormigaMesAnterior el mismo tramo del mes anterior, que es contra
 *                                lo que se compara. Va en la respuesta para que
 *                                la app pueda mostrar el "por que" del animo y
 *                                no solo la carita.
 * @param animo                   calculado en el backend, no en el cliente, para
 *                                que mobile y web muestren la misma nutria y los
 *                                umbrales se puedan ajustar sin redeployar apps.
 */
public record ResumenRespuesta(
        String mes,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal total,
        BigDecimal totalHormiga,
        BigDecimal totalHormigaMesAnterior,
        List<TotalPorCategoria> porCategoria,
        AnimoNutria animo
) {
}
