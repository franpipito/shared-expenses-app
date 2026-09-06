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

    private static BigDecimal pesos(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    @DisplayName("sin gasto hormiga la nutria esta contenta, aunque no haya historia")
    void sinHormigaEstaContenta() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("0.00"), pesos("0.00"), SIN_DATOS);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("el primer mes de uso la nutria no juzga")
    void sinDatosAnterioresEstaTranquila() {
        // No es lo mismo "el mes pasado no gaste nada evitable" que "el mes
        // pasado no usaba la app". Sin base de comparacion no hay tendencia, y
        // arrancar con una nutria preocupada el dia uno seria injusto y ademas
        // un pesimo primer contacto con el producto.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("0.00"), SIN_DATOS);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("bajar mas de 10% la pone contenta")
    void bajarLaPoneContenta() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("8000.00"), pesos("10000.00"), HAY_DATOS);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("subir mas de 10% la pone preocupada")
    void subirLaPonePreocupada() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("12000.00"), pesos("10000.00"), HAY_DATOS);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("una variacion chica no le cambia el humor")
    void variacionChicaNoLaMueve() {
        // La banda de +-10% existe justamente para esto: sin ella la nutria
        // cambiaria de cara por $500 sobre una base de $10.000, y un indicador
        // que oscila todo el tiempo deja de comunicar algo.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("10500.00"), pesos("10000.00"), HAY_DATOS);

        assertEquals(AnimoNutria.TRANQUILA, animo);
    }

    @Test
    @DisplayName("pasar de cero evitable a algo evitable es empeorar")
    void deCeroAAlgoEsEmpeorar() {
        // Caso borde: multiplicar cero por los umbrales da cero, asi que sin
        // tratarlo aparte las comparaciones no lo distinguirian.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("3000.00"), pesos("0.00"), HAY_DATOS);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("justo en el borde de la mejora cuenta como mejora")
    void bordeDeLaMejora() {
        // Exactamente -10%: la regla usa <=, asi que entra en CONTENTA.
        // Los bordes son donde viven los errores de un caracter (< contra <=).
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("9000.00"), pesos("10000.00"), HAY_DATOS);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }

    @Test
    @DisplayName("justo en el borde del empeoramiento cuenta como empeoramiento")
    void bordeDelEmpeoramiento() {
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("11000.00"), pesos("10000.00"), HAY_DATOS);

        assertEquals(AnimoNutria.PREOCUPADA, animo);
    }

    @Test
    @DisplayName("dos montos iguales con distinta escala se comparan bien")
    void escalasDistintasNoRompen() {
        // "9000.00" y "9000" son el mismo valor con distinta escala, y para
        // equals() de BigDecimal serian objetos DISTINTOS. Este test existe para
        // que si alguien cambia compareTo por equals, el test se ponga rojo.
        AnimoNutria animo = CalculadorDeAnimo.calcular(pesos("9000.00"), pesos("10000"), HAY_DATOS);

        assertEquals(AnimoNutria.CONTENTA, animo);
    }
}
