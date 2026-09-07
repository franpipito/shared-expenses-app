package com.gastoscompartidos.seguridad;

import java.util.Locale;
import java.util.Set;

/**
 * Que contrasenas se aceptan al registrarse.
 *
 * La regla NO es "una mayuscula, un numero y un simbolo". Esas reglas de
 * composicion estan desaconsejadas desde la NIST SP 800-63B, y por un motivo
 * medible: empujan a la gente hacia "Password1!", que es corta, predecible y
 * esta en todos los diccionarios de ataque. Una frase larga y comun de recordar
 * resiste muchisimo mas.
 *
 * Lo que si sirve, y es lo que hacemos:
 *   1. Exigir LARGO. Cada caracter multiplica el espacio de busqueda.
 *   2. Rechazar las que ya estan en cualquier diccionario de ataque.
 *   3. Rechazar las derivadas de datos de la propia cuenta.
 *
 * Clase pura: entran dos strings, sale una decision. Sin Spring, sin base.
 */
public final class PoliticaDeContrasenas {

    public static final int LARGO_MINIMO = 12;

    /**
     * Muestra chica y deliberada. Un diccionario real tiene millones de
     * entradas; lo correcto a escala seria consultar la API de k-anonimato de
     * Have I Been Pwned. Para dos personas que eligen su contrasena una sola
     * vez, esto ataja lo obvio sin sumar una dependencia de red al registro.
     */
    private static final Set<String> PROHIBIDAS = Set.of(
            "123456789012", "contrasena123", "password1234", "qwertyuiop12",
            "111111111111", "123123123123", "minutriaminutria", "minutria1234",
            "gastoscompartidos", "abcdefghijkl", "administrador"
    );

    private PoliticaDeContrasenas() {
    }

    /**
     * @return null si la contrasena es aceptable, o el motivo del rechazo.
     */
    public static String motivoDeRechazo(String password, String email, String nombre) {
        if (password == null || password.length() < LARGO_MINIMO) {
            return "la contrasena tiene que tener al menos " + LARGO_MINIMO + " caracteres";
        }
        // BCrypt trunca en silencio despues de 72 bytes: dos contrasenas que
        // compartan los primeros 72 serian equivalentes sin que nadie lo note.
        if (password.length() > 72) {
            return "la contrasena no puede pasar de 72 caracteres";
        }

        String normalizada = password.toLowerCase(Locale.ROOT);

        if (PROHIBIDAS.contains(normalizada)) {
            return "esa contrasena es demasiado comun, eligi otra";
        }

        // Un solo caracter repetido, por largo que sea, no aporta nada.
        if (normalizada.chars().distinct().count() < 5) {
            return "la contrasena tiene muy pocos caracteres distintos";
        }

        // Contener el propio email o nombre la vuelve adivinable para quien
        // conoce a la persona, que es justo el atacante mas probable de una app
        // de finanzas de pareja.
        String usuarioDelEmail = (email == null) ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        if (usuarioDelEmail.length() >= 4 && normalizada.contains(usuarioDelEmail)) {
            return "la contrasena no puede contener tu email";
        }
        if (nombre != null && nombre.length() >= 4
                && normalizada.contains(nombre.toLowerCase(Locale.ROOT))) {
            return "la contrasena no puede contener tu nombre";
        }

        return null;
    }
}
