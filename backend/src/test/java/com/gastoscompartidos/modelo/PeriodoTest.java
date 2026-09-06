package com.gastoscompartidos.modelo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la logica de ventanas de fecha.
 *
 * ------------------------------------------------------------------------
 * COMO SE LEE UN TEST DE JUNIT (esto no lo vas a ver mas repetido)
 *
 *  - @Test marca un metodo como caso de prueba. JUnit los descubre y los corre
 *    solos con `.\mvnw.cmd test`.
 *  - @DisplayName es el nombre legible que aparece en el reporte. Sin el, se
 *    muestra el nombre del metodo.
 *  - assertEquals(esperado, obtenido) falla el test si no coinciden. El ORDEN
 *    importa para el mensaje de error: primero lo que esperabas.
 *  - Los import static hacen que se escriba assertEquals(...) en vez de
 *    Assertions.assertEquals(...).
 *  - Cada test sigue la forma ARRANGE / ACT / ASSERT: preparar los datos,
 *    ejecutar lo que se prueba, verificar el resultado.
 *
 * Fijate que no hay base de datos, ni Spring, ni mocks. `Periodo` es un record
 * puro, asi que estos tests corren en milisegundos. Por eso vale la pena separar
 * la logica pura del resto: es la que se puede testear barato.
 * ------------------------------------------------------------------------
 *
 * Esta logica parece trivial y es exactamente donde se esconden los errores
 * silenciosos: nadie nota que la comparacion de la nutria esta corrida un dia
 * hasta que la nutria vive enojada sin motivo.
 */
class PeriodoTest {

    @Test
    @DisplayName("el mes en curso corta en hoy, incluyendolo")
    void mesEnCursoCortaEnHoy() {
        // ARRANGE: estamos a 6 de septiembre
        YearMonth septiembre = YearMonth.of(2026, 9);
        LocalDate hoy = LocalDate.of(2026, 9, 6);

        // ACT
        Periodo periodo = Periodo.transcurridoDe(septiembre, hoy);

        // ASSERT: [1 de septiembre, 7 de septiembre) = los 6 dias transcurridos
        assertEquals(LocalDate.of(2026, 9, 1), periodo.desde());
        assertEquals(LocalDate.of(2026, 9, 7), periodo.hasta());
        assertEquals(6, periodo.dias());
    }

    @Test
    @DisplayName("un mes ya cerrado se toma completo")
    void mesPasadoSeTomaCompleto() {
        Periodo periodo = Periodo.transcurridoDe(YearMonth.of(2026, 7), LocalDate.of(2026, 9, 6));

        assertEquals(LocalDate.of(2026, 7, 1), periodo.desde());
        assertEquals(LocalDate.of(2026, 8, 1), periodo.hasta());
        assertEquals(31, periodo.dias());
    }

    @Test
    @DisplayName("un mes futuro queda vacio")
    void mesFuturoQuedaVacio() {
        Periodo periodo = Periodo.transcurridoDe(YearMonth.of(2026, 12), LocalDate.of(2026, 9, 6));

        assertTrue(periodo.estaVacio());
        assertEquals(0, periodo.dias());
    }

    @Test
    @DisplayName("el tramo del mes anterior tiene la misma cantidad de dias")
    void tramoAnteriorMismaCantidadDeDias() {
        // Este es EL test que justifica que exista la clase. Si compararamos 6
        // dias de septiembre contra los 31 de agosto, la nutria estaria contenta
        // siempre a principio de mes y amargada siempre al final, por puro
        // artefacto del calendario.
        Periodo actual = Periodo.transcurridoDe(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 6));

        Periodo anterior = actual.mismoTramoDelMesAnterior();

        assertEquals(LocalDate.of(2026, 8, 1), anterior.desde());
        assertEquals(LocalDate.of(2026, 8, 7), anterior.hasta());
        assertEquals(actual.dias(), anterior.dias());
    }

    @Test
    @DisplayName("marzo completo contra febrero se recorta a los dias que febrero tiene")
    void marzoContraFebreroSeRecorta() {
        // Marzo tiene 31 dias y febrero 28. Sin recorte, la ventana anterior se
        // derramaria sobre marzo y contaria gastos del propio mes que estamos
        // midiendo.
        Periodo marzo = Periodo.transcurridoDe(YearMonth.of(2026, 3), LocalDate.of(2026, 4, 15));

        Periodo febrero = marzo.mismoTramoDelMesAnterior();

        assertEquals(LocalDate.of(2026, 2, 1), febrero.desde());
        assertEquals(LocalDate.of(2026, 3, 1), febrero.hasta());
        assertEquals(28, febrero.dias());
    }

    @Test
    @DisplayName("enero compara contra diciembre del anio anterior")
    void eneroComparaContraDiciembre() {
        Periodo enero = Periodo.transcurridoDe(YearMonth.of(2026, 1), LocalDate.of(2026, 2, 5));

        Periodo diciembre = enero.mismoTramoDelMesAnterior();

        assertEquals(LocalDate.of(2025, 12, 1), diciembre.desde());
        assertEquals(LocalDate.of(2026, 1, 1), diciembre.hasta());
    }
}
