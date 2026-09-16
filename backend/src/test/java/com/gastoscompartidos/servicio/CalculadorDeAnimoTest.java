package com.gastoscompartidos.servicio;

import com.gastoscompartidos.modelo.AnimoNutria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de la regla del animo de la nutria.
 *
 * Esta es la logica mas expuesta a errores silenciosos del proyecto: si la regla
 * esta mal, la nutria muestra una cara equivocada y nadie se entera nunca --
 * no hay excepcion, no hay log, no hay nada. Solo una usuaria que deja de
 * confiar en el indicador y despues deja de abrir la app.
 */
class CalculadorDeAnimoTest {

    private static final boolean HAY_DATOS = true;
    private static final boolean SIN_DATOS = false;

    /**
     * Un tramo de mes lo bastante largo como para que la nutria se anime a
     * comparar. Los tests que hablan de tendencia lo usan para no estar
     * probando, sin querer, la guarda de tramo corto.
     */
    private static final long TRAMO_LARGO = 15;

    private static BigDecimal pesos(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    @DisplayName("sin gasto hormiga la nutria esta contenta, aunque no haya historia")
    void sinHormigaEstaContenta() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("0.00"), pesos("0.00"), SIN_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("el primer mes de uso la nutria no juzga")
    void sinDatosAnterioresEstaTranquila() {
        // No es lo mismo "el mes pasado no gaste nada evitable" que "el mes
        // pasado no usaba la app". Sin base de comparacion no hay tendencia, y
        // arrancar con una nutria preocupada el dia uno seria injusto y ademas
        // un pesimo primer contacto con el producto.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("0.00"), SIN_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("bajar mas de 10% la pone contenta")
    void bajarLaPoneContenta() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("10000.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("subir mas de 10% la pone preocupada")
    void subirLaPonePreocupada() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("12000.00"), pesos("10000.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("una variacion chica no le cambia el humor")
    void variacionChicaNoLaMueve() {
        // La banda de +-10% existe justamente para esto: sin ella la nutria
        // cambiaria de cara por $500 sobre una base de $10.000, y un indicador
        // que oscila todo el tiempo deja de comunicar algo.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("10500.00"), pesos("10000.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("pasar de cero evitable a algo evitable es empeorar")
    void deCeroAAlgoEsEmpeorar() {
        // Caso borde: multiplicar cero por los umbrales da cero, asi que sin
        // tratarlo aparte las comparaciones no lo distinguirian.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("3000.00"), pesos("0.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("justo en el borde de la mejora cuenta como mejora")
    void bordeDeLaMejora() {
        // Exactamente -10%: la regla usa <=, asi que entra en CONTENTA.
        // Los bordes son donde viven los errores de un caracter (< contra <=).
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("9000.00"), pesos("10000.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("justo en el borde del empeoramiento cuenta como empeoramiento")
    void bordeDelEmpeoramiento() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("11000.00"), pesos("10000.00"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("dos montos iguales con distinta escala se comparan bien")
    void escalasDistintasNoRompen() {
        // "9000.00" y "9000" son el mismo valor con distinta escala, y para
        // equals() de BigDecimal serian objetos DISTINTOS. Este test existe para
        // que si alguien cambia compareTo por equals, el test se ponga rojo.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("9000.00"), pesos("10000"), HAY_DATOS, TRAMO_LARGO);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("el mes recien arrancado la nutria no compara: un dia contra un dia es ruido")
    void tramoCortoEstaTranquila() {
        // El 1 de cada mes, `Periodo.transcurridoDe` da UN dia, y el tramo del
        // mes anterior tambien. Ahi la banda de +-10% no protege de nada: la
        // muestra es demasiado chica para que la diferencia signifique algo.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("1000.00"), HAY_DATOS, 1);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("un cafe el dia 1 no deja a la nutria preocupada")
    void tramoCortoNoSeAsustaConCeroAnterior() {
        // ESTE ES EL CASO QUE MOTIVO LA REGLA. Si el 1 del mes pasado no hubo ni
        // un gasto evitable, `hormigaAnterior` es cero, y la guarda de "el mes
        // anterior no tuvo nada y este si" devolvia PREOCUPADA. O sea que un
        // solo cafe el primer dia del mes arrancaba con la nutria preocupada, en
        // una app cuyo riesgo numero uno es el abandono.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("3500.00"), pesos("0.00"), HAY_DATOS, 1);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("pasado el tramo minimo vuelve a comparar normalmente")
    void pasadoElTramoMinimoCompara() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("1000.00"), HAY_DATOS, 5);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("cero hormiga sigue siendo CONTENTA aunque el tramo sea corto")
    void ceroHormigaNoNecesitaMuestra() {
        // "No gastaste nada evitable" es verdad el dia 1 igual que el dia 20: no
        // es una comparacion, asi que no necesita muestra. Por eso la guarda de
        // tramo corto va DESPUES de la de cero.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("0.00"), pesos("5000.00"), HAY_DATOS, 1);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }
}
