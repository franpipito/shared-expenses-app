package com.gastoscompartidos.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la politica de contrasenas.
 *
 * Otra clase pura, sin Spring ni base: entran dos strings y sale una decision.
 */
class PoliticaDeContrasenasTest {

    private static final String EMAIL = "viole@minutria.app";
    private static final String NOMBRE = "Viole";

    @Test
    @DisplayName("una frase larga y variada se acepta")
    void fraseLargaSeAcepta() {
        assertNull(PoliticaDeContrasenas.motivoDeRechazo(
                "nutria-en-el-rio-2026", EMAIL, NOMBRE));
    }

    @Test
    @DisplayName("la contrasena del entorno de desarrollo pasa la politica")
    void laDelSmokeTestPasa() {
        // Si esto se pusiera rojo, el smoke test dejaria de poder registrarse y
        // el error aparecerian veinte chequeos mas abajo, sin relacion aparente.
        assertNull(PoliticaDeContrasenas.motivoDeRechazo(
                "gastos-dev-2026", "franco@local", "Franco"));
    }

    @Test
    @DisplayName("menos de 12 caracteres se rechaza")
    void muyCortaSeRechaza() {
        String motivo = PoliticaDeContrasenas.motivoDeRechazo("Abc123!x", EMAIL, NOMBRE);

        assertNotNull(motivo);
        assertTrue(motivo.contains("12"), "el mensaje deberia decir cuantos caracteres faltan");
    }

    @Test
    @DisplayName("mas de 72 caracteres se rechaza, porque BCrypt trunca en silencio")
    void demasiadoLargaSeRechaza() {
        // BCrypt ignora todo lo que pase de 72 bytes. Sin este limite, dos
        // contrasenas larguisimas que compartan el principio serian la misma y
        // nadie se enteraria.
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo("a".repeat(73) + "bcdef", EMAIL, NOMBRE));
    }

    @Test
    @DisplayName("una contrasena comun se rechaza aunque tenga largo suficiente")
    void comunSeRechaza() {
        // El largo por si solo no alcanza: esta tiene 12 caracteres y esta en
        // cualquier diccionario de ataque.
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo("123456789012", EMAIL, NOMBRE));
    }

    @Test
    @DisplayName("repetir un caracter no cuenta como largo")
    void pocosCaracteresDistintosSeRechaza() {
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo("aaaaaaaaaaaaaa", EMAIL, NOMBRE));
    }

    @Test
    @DisplayName("no puede contener el propio email")
    void conElEmailSeRechaza() {
        // "violeta" contiene "viole", que es la parte local del email.
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo("violeta-de-jardin", EMAIL, NOMBRE));
    }

    @Test
    @DisplayName("no puede contener el propio nombre")
    void conElNombreSeRechaza() {
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo(
                "franco-verduras-12", "otro@local", "Franco"));
    }

    @Test
    @DisplayName("null se rechaza sin explotar")
    void nullSeRechaza() {
        assertNotNull(PoliticaDeContrasenas.motivoDeRechazo(null, EMAIL, NOMBRE));
    }
}
