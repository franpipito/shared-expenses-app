package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.LoginRequest;
import com.gastoscompartidos.dto.RegistroRequest;
import com.gastoscompartidos.dto.TokenRespuesta;
import com.gastoscompartidos.servicio.AutenticacionServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las dos unicas rutas publicas de la API. Todo lo demas exige token.
 */
@RestController
@RequestMapping("/auth")
public class AutenticacionControlador {

    private final AutenticacionServicio servicio;

    public AutenticacionControlador(AutenticacionServicio servicio) {
        this.servicio = servicio;
    }

    /** POST /auth/registro */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenRespuesta registro(@Valid @RequestBody RegistroRequest req) {
        return servicio.registrar(req);
    }

    /** POST /auth/login */
    @PostMapping("/login")
    public TokenRespuesta login(@Valid @RequestBody LoginRequest req) {
        return servicio.login(req);
    }
}
