package com.gastoscompartidos.modelo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Un rango de fechas SEMIABIERTO: incluye `desde`, excluye `hasta`.
 *
 * Que sea semiabierto no es capricho. Con rangos cerrados hay que saber si el
 * mes termina el 28, 30 o 31, y esa clase de detalle es de donde salen los
 * errores silenciosos de fecha. Con [desde, hasta) el mes de septiembre es
 * simplemente [2026-09-01, 2026-10-01) y no hay que pensar.
 *
 * Es un record: inmutable, sin dependencias, sin base de datos. Por eso se puede
 * testear con JUnit sin levantar nada.
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    /**
     * El tramo TRANSCURRIDO de un mes.
     *
     * Si el mes pedido es el actual, corta en hoy (inclusive). Si es un mes
     * pasado, toma el mes completo. Si es futuro, queda vacio.
     *
     * Esto es lo que hace justa la comparacion de la nutria: el 6 de septiembre
     * no se puede comparar contra todo agosto, porque cualquier mes va perdiendo
     * contra un mes completo. Hay que comparar 6 dias contra 6 dias.
     */
    public static Periodo transcurridoDe(YearMonth mes, LocalDate hoy) {
        LocalDate primerDia = mes.atDay(1);
        LocalDate finDelMes = mes.plusMonths(1).atDay(1);

        if (hoy.isBefore(primerDia)) {
            return new Periodo(primerDia, primerDia);      // mes futuro: vacio
        }
        if (hoy.isBefore(finDelMes)) {
            return new Periodo(primerDia, hoy.plusDays(1)); // mes en curso
        }
        return new Periodo(primerDia, finDelMes);           // mes ya cerrado
    }

    /**
     * El mismo tramo, pero del mes anterior.
     *
     * Se recorta si el mes anterior es mas corto: los 31 dias de marzo comparan
     * contra los 28 o 29 de febrero, no contra 31 dias que se derramarian sobre
     * marzo. Es imperfecto -- comparar 31 dias contra 28 favorece a febrero --
     * pero es preferible a contar dias que pertenecen a otro mes.
     */
    public Periodo mismoTramoDelMesAnterior() {
        YearMonth mesAnterior = YearMonth.from(desde).minusMonths(1);
        LocalDate inicio = mesAnterior.atDay(1);
        LocalDate finDelMesAnterior = mesAnterior.plusMonths(1).atDay(1);

        LocalDate fin = inicio.plusDays(dias());
        if (fin.isAfter(finDelMesAnterior)) {
            fin = finDelMesAnterior;
        }
        return new Periodo(inicio, fin);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(desde, hasta);
    }

    public boolean estaVacio() {
        return dias() <= 0;
    }
}
