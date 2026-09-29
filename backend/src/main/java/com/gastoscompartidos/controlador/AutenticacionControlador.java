package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.BorrarCuentaRequest;
import com.gastoscompartidos.dto.LoginRequest;
import com.gastoscompartidos.dto.OlvideContrasenaRequest;
import com.gastoscompartidos.dto.RegistroRequest;
import com.gastoscompartidos.dto.RestablecerContrasenaRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.servicio.AutenticacionServicio;
import com.gastoscompartidos.servicio.CuentaServicio;
import com.gastoscompartidos.servicio.RecuperacionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /auth/registro y /auth/login son las unicas rutas publicas de la API.
 * /auth/cerrar-sesiones exige token, como todo lo demas.
 */
@RestController
@RequestMapping("/auth")
public class AutenticacionControlador {

    private final AutenticacionServicio servicio;
    private final CuentaServicio cuentas;
    private final RecuperacionServicio recuperacion;

    public AutenticacionControlador(AutenticacionServicio servicio, CuentaServicio cuentas,
                                    RecuperacionServicio recuperacion) {
        this.servicio = servicio;
        this.cuentas = cuentas;
        this.recuperacion = recuperacion;
    }

    /**
     * POST /auth/registro
     *
     * La IP se saca aca y se le pasa al servicio como un String cualquiera. Es
     * a proposito: el servicio limita intentos sin tener que saber que existe
     * HTTP, y por lo tanto se puede testear pasandole "1.2.3.4".
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenRespuesta registro(@Valid @RequestBody RegistroRequest req,
                                   HttpServletRequest http) {
        return servicio.registrar(req, ipDe(http));
    }

    /** POST /auth/login */
    @PostMapping("/login")
    public TokenRespuesta login(@Valid @RequestBody LoginRequest req,
                                HttpServletRequest http) {
        return servicio.login(req, ipDe(http));
    }

    /**
     * POST /auth/cerrar-sesiones
     *
     * Invalida todos los tokens del usuario, incluido el que uso para esta
     * llamada, y devuelve uno nuevo. Es el boton de "perdi el celular".
     */
    @PostMapping("/cerrar-sesiones")
    public TokenRespuesta cerrarSesiones() {
        return servicio.cerrarOtrasSesiones();
    }

    /**
     * POST /auth/borrar-cuenta
     *
     * POST y no DELETE, y es una decision: hace falta mandar la contrasena en el
     * cuerpo, y un cuerpo en un DELETE no tiene semantica definida en HTTP --
     * hay proxies y clientes que lo descartan. Ademas queda al lado de
     * `cerrar-sesiones`, que es la otra accion sobre la propia cuenta.
     *
     * 204: no hay nada que devolver, la cuenta ya no existe.
     */
    @PostMapping("/borrar-cuenta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarCuenta(@Valid @RequestBody BorrarCuentaRequest req) {
        cuentas.borrar(req);
    }

    /**
     * POST /auth/olvide-contrasena
     *
     * 204 siempre, exista o no el email: ver RecuperacionServicio.
     */
    @PostMapping("/olvide-contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olvideContrasena(@Valid @RequestBody OlvideContrasenaRequest req,
                                 HttpServletRequest http) {
        recuperacion.pedirCodigo(req, ipDe(http));
    }

    /** POST /auth/restablecer-contrasena: codigo + contrasena nueva, y queda adentro. */
    @PostMapping("/restablecer-contrasena")
    public TokenRespuesta restablecerContrasena(@Valid @RequestBody RestablecerContrasenaRequest req) {
        return recuperacion.restablecer(req);
    }

    private String ipDe(HttpServletRequest http) {
        String remoto = http.getRemoteAddr();
        return (remoto == null || remoto.isBlank()) ? "desconocida" : remoto;
    }
}
