package com.gastoscompartidos.servicio;

import com.gastoscompartidos.modelo.AnimoNutria;

import java.math.BigDecimal;

/**
 * La regla del animo de la nutria, aislada del resto del mundo.
 *
 * No toca la base, no depende de Spring, no tiene estado. Entran tres valores y
 * sale un animo. Por eso se puede testear a mano en microsegundos, que es
 * justamente lo que queremos para la logica mas propensa a errores silenciosos.
 */
public final class CalculadorDeAnimo {

    /** Baja de al menos 10% para considerarlo una mejora. */
    private static final BigDecimal UMBRAL_MEJORA = new BigDecimal("0.90");

    /** Sube al menos 10% para considerarlo un empeoramiento. */
    private static final BigDecimal UMBRAL_EMPEORAMIENTO = new BigDecimal("1.10");

    private CalculadorDeAnimo() {
        // clase de utilidad: no se instancia
    }

    /**
     * La banda de +-10% existe para que la nutria no cambie de humor por ruido.
     * Sin ella, gastar $100 mas que el mes pasado en una base de $50.000 la
     * pondria preocupada, y ese vaiven constante hace que el indicador deje de
     * significar algo.
     *
     * @param hormigaActual        gasto hormiga del tramo transcurrido del mes
     * @param hormigaAnterior      gasto hormiga del mismo tramo del mes anterior
     * @param hayDatosAnteriores   si el mes anterior tiene algun gasto cargado
     */
    /**
     * Dias minimos de mes transcurrido antes de animarse a hablar de tendencia.
     *
     * LA BANDA DE +-10% NO ALCANZA CUANDO LA MUESTRA ES CHICA, y el 1 de cada
     * mes la muestra es de UN dia contra UN dia. Ahi la banda no protege de
     * nada: si el 1 del mes pasado no hubo ni un gasto evitable,
     * `hormigaAnterior` es cero y un solo cafe del dia 1 deja a la nutria
     * PREOCUPADA.
     *
     * Cinco dias es un numero elegido, no derivado: es el tramo mas corto en el
     * que un fin de semana caro no domina la comparacion el solo. Si se quiere
     * mover, se mueve aca y los tests de abajo dicen que se rompe.
     *
     * El costo, y hay que tenerlo presente: durante los primeros dias de cada
     * mes la nutria no opina. Se paga con el copy -- "recien arranca el mes"
     * informa sin juzgar, y la ilustracion sigue en pantalla, que es lo que
     * sostiene el habito de abrir la app.
     */
    private static final int DIAS_MINIMOS_PARA_COMPARAR = 5;

    public static AnimoNutria calcular(BigDecimal hormigaActual,
                                       BigDecimal hormigaAnterior,
                                       boolean hayDatosAnteriores,
                                       long diasTranscurridos) {

        // Cero gasto evitable es el mejor resultado posible, sin importar el pasado.
        if (hormigaActual.signum() == 0) {
            return AnimoNutria.CONTENTA;
        }

        // Sin base de comparacion no se puede hablar de tendencia. El primer mes
        // de uso la nutria no juzga.
        if (!hayDatosAnteriores) {
            return AnimoNutria.TRANQUILA;
        }

        // Y con el mes recien arrancado tampoco: ver DIAS_MINIMOS_PARA_COMPARAR.
        // Va DESPUES del chequeo de cero hormiga a proposito -- "no gastaste
        // nada evitable" es verdad el dia 1 igual que el dia 20, no es una
        // comparacion y no necesita muestra.
        if (diasTranscurridos < DIAS_MINIMOS_PARA_COMPARAR) {
            return AnimoNutria.TRANQUILA;
        }

        // El mes anterior no tuvo ni un peso evitable y este si: eso es subir.
        // Se chequea aparte porque multiplicar cero por los umbrales da cero y
        // las comparaciones de abajo no distinguirian el caso.
        if (hormigaAnterior.signum() == 0) {
            return AnimoNutria.PREOCUPADA;
        }

        // compareTo y no equals: equals compara tambien la escala, asi que
        // 5000.0 no seria "igual" a 5000.00. Es la trampa clasica de BigDecimal.
        if (hormigaActual.compareTo(hormigaAnterior.multiply(UMBRAL_MEJORA)) <= 0) {
            return AnimoNutria.CONTENTA;
        }
        if (hormigaActual.compareTo(hormigaAnterior.multiply(UMBRAL_EMPEORAMIENTO)) >= 0) {
            return AnimoNutria.PREOCUPADA;
        }
        return AnimoNutria.TRANQUILA;
    }
}
