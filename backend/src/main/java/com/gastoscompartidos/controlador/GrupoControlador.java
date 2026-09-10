package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.GrupoRespuesta;
import com.gastoscompartidos.servicio.GrupoServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El grupo del usuario autenticado.
 *
 * Una sola ruta y sin parametros: no hay `{id}` que validar porque el grupo sale
 * del token. Ver {@link GrupoServicio}.
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
}
