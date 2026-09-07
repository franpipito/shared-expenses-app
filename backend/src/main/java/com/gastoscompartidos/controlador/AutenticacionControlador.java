package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.LoginRequest;
import com.gastoscompartidos.dto.RegistroRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.servicio.AutenticacionServicio;
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

    public AutenticacionControlador(AutenticacionServicio servicio) {
        this.servicio = servicio;
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

    private String ipDe(HttpServletRequest http) {
        String remoto = http.getRemoteAddr();
        return (remoto == null || remoto.isBlank()) ? "desconocida" : remoto;
    }
}
