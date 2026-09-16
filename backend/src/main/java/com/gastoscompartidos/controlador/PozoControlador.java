package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.AporteRequest;
import com.gastoscompartidos.dto.CrearPozoRequest;
import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.PozoRespuesta;
import com.gastoscompartidos.servicio.PozoServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de la vaquita. Como todos los controladores de esta app: reciben,
 * delegan y devuelven. No deciden nada.
 */
@RestController
@RequestMapping("/pozos")
public class PozoControlador {

    private final PozoServicio servicio;

    public PozoControlador(PozoServicio servicio) {
        this.servicio = servicio;
    }

    /** POST /pozos — abre la vaquita. Solo puede haber una abierta por grupo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PozoRespuesta crear(@Valid @RequestBody CrearPozoRequest req) {
        return servicio.crear(req);
    }

    /**
     * GET /pozos — todas las vaquitas del grupo, incluidas las cerradas.
     *
     * Existe para que una vaquita cerrada no quede inalcanzable. Sus gastos no
     * aparecen en la lista del mes, y `/pozos/activo` deja de devolverla apenas
     * se cierra: sin este listado no habia forma de recuperar su id para llegar
     * a `GET /pozos/{id}/gastos` y corregir un monto mal cargado.
     */
    @GetMapping
    public List<PozoRespuesta> listar() {
        return servicio.listar();
    }

    /**
     * GET /pozos/activo — la vaquita abierta con sus numeros.
     *
     * Devuelve **204 No Content** y no un 404 cuando no hay ninguna abierta: no
     * tener vaquita es un estado normal de la app, no un error. El cliente
     * pregunta "hay una?" y "no" es una respuesta valida, no un fallo que haya
     * que mostrarle a nadie.
     *
     * Por eso el tipo de retorno es ResponseEntity: es la forma de elegir el
     * codigo de estado en tiempo de ejecucion. Cuando el codigo es siempre el
     * mismo alcanza con @ResponseStatus, como en los otros metodos.
     */
    @GetMapping("/activo")
    public ResponseEntity<PozoRespuesta> activo() {
        return servicio.activo()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** GET /pozos/{id}/gastos — los gastos del viaje, sin recorte por mes. */
    @GetMapping("/{id}/gastos")
    public List<GastoRespuesta> gastos(@PathVariable String id) {
        return servicio.gastosDe(id);
    }

    /** POST /pozos/{id}/aportes — poner plata. Siempre a nombre de quien la pone. */
    @PostMapping("/{id}/aportes")
    public PozoRespuesta aportar(@PathVariable String id, @Valid @RequestBody AporteRequest req) {
        return servicio.aportar(id, req);
    }

    /** POST /pozos/{id}/cerrar — terminó el viaje. No devuelve el sobrante: eso lo arreglan ellos. */
    @PostMapping("/{id}/cerrar")
    public PozoRespuesta cerrar(@PathVariable String id) {
        return servicio.cerrar(id);
    }
}
