package com.gastoscompartidos.modelo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la aritmetica de la vaquita.
 *
 * `Pozo` es una clase comun de Java: no tiene Spring adentro y no necesita una
 * base para ejercitarse. Es la misma razon por la que `CalculadorDeAnimo` y
 * `Periodo` concentran toda la cobertura de tests de este proyecto -- la logica
 * que importa vive en objetos que se pueden construir con `new`.
 *
 * Lo que NO se testea aca, y hay que tenerlo claro: el $push atomico, el indice
 * parcial unico y los filtros `sinPozo()` necesitan una base de verdad. Eso lo
 * cubre `scripts/smoke-test.ps1` contra el backend corriendo.
 */
class PozoTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 9, 30);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 5);

    private static Pozo bariloche() {
        return new Pozo("grupo1", "Bariloche", new BigDecimal("800000.00"), DESDE, HASTA);
    }

    /**
     * Los aportes se escriben con $push y no con save(), asi que la unica forma
     * de armar un pozo con aportes para un test es por reflexion. Es feo, y es
     * la contracara honesta de haber elegido la escritura atomica: el precio de
     * no tener un setter publico que nadie deberia usar en produccion.
     */
    private static Pozo conAportes(Pozo pozo, Aporte... aportes) {
        try {
            var campo = Pozo.class.getDeclaredField("aportes");
            campo.setAccessible(true);
            campo.set(pozo, List.of(aportes));
            return pozo;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Aporte aporte(String usuarioId, String nombre, String monto) {
        return new Aporte(new ReferenciaUsuario(usuarioId, nombre),
                new BigDecimal(monto), DESDE);
    }

    @Nested
    @DisplayName("Cuanto hay en el pozo")
    class TotalAportado {

        @Test
        @DisplayName("un pozo sin aportes tiene 0.00, no 0")
        void pozoVacio() {
            // La escala importa: el cliente muestra el numero tal cual viene, y
            // "0" en vez de "0.00" se ve como un bug en una pantalla de plata.
            assertThat(bariloche().totalAportado()).isEqualByComparingTo("0.00");
            assertThat(bariloche().totalAportado().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("suma los aportes de los dos")
        void sumaLosDos() {
            Pozo pozo = conAportes(bariloche(),
                    aporte("franco", "Franco", "400000.00"),
                    aporte("viole", "Viole", "400000.00"));

            assertThat(pozo.totalAportado()).isEqualByComparingTo("800000.00");
        }

        @Test
        @DisplayName("suma varios aportes de la misma persona")
        void variosDeLaMisma() {
            Pozo pozo = conAportes(bariloche(),
                    aporte("franco", "Franco", "200000.00"),
                    aporte("franco", "Franco", "200000.00"),
                    aporte("viole", "Viole", "400000.00"));

            assertThat(pozo.totalAportado()).isEqualByComparingTo("800000.00");
            assertThat(pozo.aportadoPor("franco")).isEqualByComparingTo("400000.00");
        }

        @Test
        @DisplayName("quien no aporto tiene 0.00, no se lo omite")
        void elQueNoAporto() {
            Pozo pozo = conAportes(bariloche(), aporte("franco", "Franco", "400000.00"));

            // "Viole $0" es informacion util, no un hueco: es justo lo que hay
            // que mirar antes de salir de viaje.
            assertThat(pozo.aportadoPor("viole")).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("los centavos no se pierden")
        void centavos() {
            Pozo pozo = conAportes(bariloche(),
                    aporte("franco", "Franco", "0.10"),
                    aporte("franco", "Franco", "0.20"));

            // Con double esto daria 0.30000000000000004. Es todo el motivo por
            // el que los montos son BigDecimal y se guardan como Decimal128.
            assertThat(pozo.totalAportado()).isEqualByComparingTo("0.30");
        }
    }

    @Nested
    @DisplayName("El invariante: restante = aportado - gastado")
    class Restante {

        @Test
        @DisplayName("descuenta lo gastado")
        void descuenta() {
            Pozo pozo = conAportes(bariloche(),
                    aporte("franco", "Franco", "400000.00"),
                    aporte("viole", "Viole", "400000.00"));

            assertThat(pozo.restante(new BigDecimal("120000.00")))
                    .isEqualByComparingTo("680000.00");
        }

        @Test
        @DisplayName("se puede pasar de la vaquita y el restante queda negativo")
        void sobregiro() {
            Pozo pozo = conAportes(bariloche(),
                    aporte("franco", "Franco", "400000.00"),
                    aporte("viole", "Viole", "400000.00"));

            // NO es un error: si se acabo la vaquita en medio de una cena, el
            // gasto se carga igual y el pozo queda en rojo. Bloquear una carga
            // parada en el mostrador es el pecado capital de esta app.
            assertThat(pozo.restante(new BigDecimal("850000.00")))
                    .isEqualByComparingTo("-50000.00");
        }

        @Test
        @DisplayName("un pozo sin aportes y sin gastos da 0.00")
        void reciennacido() {
            assertThat(bariloche().restante(new BigDecimal("0.00")))
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Si hoy cae dentro del viaje")
    class Vigencia {

        @Test
        @DisplayName("el primer y el ultimo dia estan adentro")
        void bordesInclusivos() {
            // A diferencia del rango de los gastos, que es semiabierto
            // [desde, hasta), este es cerrado por los dos lados: el ultimo dia
            // del viaje todavia es viaje.
            assertThat(bariloche().vigenteEl(DESDE)).isTrue();
            assertThat(bariloche().vigenteEl(HASTA)).isTrue();
        }

        @Test
        @DisplayName("antes y despues del viaje, no")
        void afuera() {
            assertThat(bariloche().vigenteEl(DESDE.minusDays(1))).isFalse();
            assertThat(bariloche().vigenteEl(HASTA.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("un pozo sin fechas esta vigente siempre, mientras este abierto")
        void sinFechas() {
            Pozo sinFechas = new Pozo("grupo1", "Bariloche", null, null, null);

            assertThat(sinFechas.vigenteEl(LocalDate.of(2027, 1, 1))).isTrue();
        }

        @Test
        @DisplayName("un pozo cerrado nunca esta vigente, aunque hoy caiga en el rango")
        void cerrado() {
            // De esto depende que el formulario de alta no abra con "Vaquita"
            // puesto despues de cerrar el viaje. Es la diferencia entre que un
            // gasto de octubre entre a la vaquita o no.
            Pozo pozo = bariloche();
            cerrar(pozo);

            assertThat(pozo.vigenteEl(DESDE)).isFalse();
        }

        private void cerrar(Pozo pozo) {
            try {
                var campo = Pozo.class.getDeclaredField("estado");
                campo.setAccessible(true);
                campo.set(pozo, EstadoPozo.CERRADO);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    @DisplayName("la lista de aportes que sale del pozo no se puede modificar desde afuera")
    void aportesInmutables() {
        Pozo pozo = conAportes(bariloche(), aporte("franco", "Franco", "400000.00"));

        // getAportes() devuelve una copia inmutable. Sin eso, cualquiera con una
        // referencia al pozo podria agregar un aporte que nunca se escribio en
        // la base -- un numero de plata inventado que se ve real en pantalla.
        assertThat(pozo.getAportes()).hasSize(1);
        assertThat(pozo.getAportes().getClass().getSimpleName()).doesNotContain("ArrayList");
    }
}
