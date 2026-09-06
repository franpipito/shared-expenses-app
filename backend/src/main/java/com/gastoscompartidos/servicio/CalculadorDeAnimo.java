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
    public static AnimoNutria calcular(BigDecimal hormigaActual,
                                       BigDecimal hormigaAnterior,
                                       boolean hayDatosAnteriores) {

        // Cero gasto evitable es el mejor resultado posible, sin importar el pasado.
        if (hormigaActual.signum() == 0) {
            return AnimoNutria.CONTENTA;
        }

        // Sin base de comparacion no se puede hablar de tendencia. El primer mes
        // de uso la nutria no juzga.
        if (!hayDatosAnteriores) {
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
