package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.GrupoRespuesta;
import com.gastoscompartidos.dto.InvitacionRespuesta;
import com.gastoscompartidos.dto.SalirDelGrupoRequest;
import com.gastoscompartidos.dto.SumarseRequest;
import com.gastoscompartidos.servicio.GrupoServicio;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El grupo del usuario autenticado: leerlo, invitar, sumarse y salir.
 *
 * Ninguna ruta acepta un id: el grupo sobre el que se actua sale siempre del
 * token, nunca de la request. Ver {@link GrupoServicio}.
 *
 * Queda cubierto por `anyRequest().authenticated()` de ConfiguracionSeguridad,
 * asi que no hace falta declarar nada: lo que NO esta explicitamente abierto,
 * esta cerrado. Es el default correcto -- al reves, olvidarse de proteger una
 * ruta nueva seria silencioso.
 */
@RestController
@RequestMapping("/grupo")
public class GrupoControlador {

    private final GrupoServicio servicio;

    public GrupoControlador(GrupoServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /grupo */
    @GetMapping
    public GrupoRespuesta mio() {
        return servicio.mio();
    }

    /** POST /grupo/invitar: genera (o regenera) el codigo para sumar a alguien. */
    @PostMapping("/invitar")
    public InvitacionRespuesta invitar() {
        return servicio.invitar();
    }

    /** POST /grupo/sumarse: se suma al grupo dueño de ese codigo. */
    @PostMapping("/sumarse")
    public GrupoRespuesta sumarse(@Valid @RequestBody SumarseRequest req) {
        return servicio.sumarse(req);
    }

    /** POST /grupo/salir: deja el grupo compartido y vuelve a uno propio. */
    @PostMapping("/salir")
    public GrupoRespuesta salir(@Valid @RequestBody SalirDelGrupoRequest req) {
        return servicio.salir(req);
    }
}
