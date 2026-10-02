package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.EstadoPozo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El pozo con sus numeros resueltos.
 *
 * `aportado`, `gastado` y `restante` NO estan guardados en ningun lado: se
 * calculan en cada lectura, igual que el resumen y el saldo. Es la misma
 * decision que el CLAUDE.md justifica para los agregados mensuales, y aca el
 * caso es todavia mas facil: un viaje son decenas de documentos.
 *
 * @param restante  aportado - gastado. **Puede ser negativo**, y eso no es un
 *                  error: si se les acabo la vaquita en medio de una cena, el
 *                  gasto se carga igual y el pozo queda en rojo. Bloquear una
 *                  carga parada en el mostrador es el pecado capital de esta
 *                  app. Validar no es lo mismo que bloquear.
 * @param porPersona cuanto puso cada uno. De aca sale la unica deuda que un
 *                  pozo puede generar: si aportaron distinto, la mitad de esa
 *                  diferencia.
 * @param gastadoPorPersona cuanto gasto cada uno DEL POZO (quien lo pago, no
 *                  quien lo disfruto). A diferencia de {@code porPersona},
 *                  esto NO genera ninguna deuda -- es puramente informativo,
 *                  para contestar "¿quién gastó más en el viaje?" sin tocar
 *                  el invariante de que gastar del pozo no mueve la cuenta
 *                  entre ellos. Ver docs/vaquita.md, sección 10.
 * @param sobrantePorPersona {@code porPersona - gastadoPorPersona}, persona por
 *                  persona. A diferencia de los dos anteriores, ESTE SI es el
 *                  numero que decide la plata real: al cerrar el viaje, es
 *                  cuanto le corresponde devolver a cada uno del pozo.
 *                  **Puede dar negativo** -- significa que esa persona gasto
 *                  del pozo mas de lo que aporto, y le tiene que devolver esa
 *                  diferencia al pozo (o a la otra persona) antes de repartir
 *                  el resto. No se oculta ni se clampea en cero: igual que el
 *                  pozo entero ya puede sobregirarse a proposito, esto es lo
 *                  mismo un nivel mas abajo. Ver docs/vaquita.md, sección 11.
 * @param vigente   si hoy cae dentro de las fechas del viaje. El cliente lo usa
 *                  para decidir si el alta de gasto abre con "Vaquita" puesto,
 *                  y viene resuelto del backend porque "hoy" depende de la zona
 *                  horaria -- el mismo motivo que el bean Clock.
 */
public record PozoRespuesta(
        String id,
        String nombre,
        BigDecimal objetivo,
        EstadoPozo estado,
        LocalDate desde,
        LocalDate hasta,
        boolean vigente,
        BigDecimal aportado,
        BigDecimal gastado,
        BigDecimal restante,
        List<TotalPorPersona> porPersona,
        List<TotalPorPersona> gastadoPorPersona,
        List<TotalPorPersona> sobrantePorPersona,
        List<AporteRespuesta> aportes,
        Long version
) {
}
