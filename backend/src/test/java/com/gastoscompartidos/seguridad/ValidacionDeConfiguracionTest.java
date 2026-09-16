package com.gastoscompartidos.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests de la validacion que decide si la app arranca en produccion.
 *
 * Se testea por el constructor y no por los metodos privados: lo que importa no
 * es como parsea, sino si deja arrancar o no. Un test que llame al parser por
 * dentro se rompe cuando alguien lo reescribe, aunque el comportamiento sea el
 * mismo.
 *
 * Es una clase pura -- no toca Spring ni la base -- asi que corre en
 * milisegundos, igual que `CalculadorDeAnimoTest` y `PeriodoTest`.
 */
class ValidacionDeConfiguracionTest {

    private static final String SECRETO_OK = "un-secreto-largo-y-generado-al-azar";
    private static final String CODIGO_OK = "no-es-nutrias";

    private static void validar(String mongoUri) {
        new ValidacionDeConfiguracion(SECRETO_OK, CODIGO_OK, mongoUri);
    }

    @Nested
    @DisplayName("El nombre de la base en MONGO_URI")
    class NombreDeBase {

        @Test
        @DisplayName("la cadena que da Atlas, pegada tal cual, no arranca")
        void sinBase() {
            // Este es EL caso: Atlas te da la cadena sin el nombre de la base, y
            // el driver caeria en `test` sin decir nada.
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb+srv://u:c@cluster0.abc.mongodb.net/?retryWrites=true&w=majority"));
        }

        @Test
        @DisplayName("con la barra pero sin nombre tampoco")
        void barraSola() {
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb+srv://u:c@cluster0.abc.mongodb.net/"));
        }

        @Test
        @DisplayName("sin barra ni parametros tampoco")
        void soloElHost() {
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb+srv://u:c@cluster0.abc.mongodb.net"));
        }

        @Test
        @DisplayName("con el nombre puesto, arranca")
        void conBase() {
            assertDoesNotThrow(() ->
                    validar("mongodb+srv://u:c@cluster0.abc.mongodb.net/gastos?retryWrites=true&w=majority"));
        }

        @Test
        @DisplayName("con el nombre y sin parametros, arranca")
        void conBaseSinParametros() {
            assertDoesNotThrow(() ->
                    validar("mongodb+srv://u:c@cluster0.abc.mongodb.net/gastos"));
        }

        @Test
        @DisplayName("una arroba en la contrasena no confunde al parser")
        void arrobaEnLaContrasena() {
            // Por esto se busca el ULTIMO '@' y no el primero. Con el primero,
            // el parser cortaria en el medio de la contrasena y encontraria la
            // barra de "c@n/tra", dando por bueno algo que no lo es.
            assertDoesNotThrow(() ->
                    validar("mongodb+srv://usuario:c%40ntra@cluster0.abc.mongodb.net/gastos"));
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb+srv://usuario:c%40ntra@cluster0.abc.mongodb.net/"));
        }
    }

    @Nested
    @DisplayName("Los valores de desarrollo")
    class ValoresDeDesarrollo {

        private static final String URI_OK = "mongodb+srv://u:c@cluster0.abc.mongodb.net/gastos";

        @Test
        @DisplayName("el secreto del repo no arranca")
        void secretoDeDesarrollo() {
            assertThrows(IllegalStateException.class, () -> new ValidacionDeConfiguracion(
                    "secreto-de-desarrollo-NO-USAR-EN-PRODUCCION-esta-en-el-repo", CODIGO_OK, URI_OK));
        }

        @Test
        @DisplayName("el codigo de invitacion del repo no arranca")
        void codigoDeDesarrollo() {
            assertThrows(IllegalStateException.class, () ->
                    new ValidacionDeConfiguracion(SECRETO_OK, "nutrias", URI_OK));
        }

        @Test
        @DisplayName("apuntar a la base local no arranca")
        void baseLocal() {
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb://gastos:algo@localhost:27017/gastos?authSource=admin"));
        }

        @Test
        @DisplayName("la contrasena local no arranca aunque el host sea de Atlas")
        void contrasenaLocal() {
            assertThrows(IllegalStateException.class, () ->
                    validar("mongodb+srv://gastos:gastos_local@cluster0.abc.mongodb.net/gastos"));
        }

        @Test
        @DisplayName("todo en orden, arranca")
        void todoBien() {
            assertDoesNotThrow(() -> validar(URI_OK));
        }
    }
}
