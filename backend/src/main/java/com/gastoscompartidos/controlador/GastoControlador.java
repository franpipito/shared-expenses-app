package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.GastoRespuesta;
import com.gastoscompartidos.dto.GuardarGastoRequest;
import com.gastoscompartidos.servicio.GastoServicio;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/**
 * Endpoints de gastos.
 *
 * Todos requieren el header X-Usuario-Id (ver UsuarioActualPorHeader). En la
 * sesion 4 eso pasa a ser un JWT y este controlador no cambia una linea.
 */
@RestController
@RequestMapping("/gastos")
public class GastoControlador {

    private final GastoServicio servicio;

    public GastoControlador(GastoServicio servicio) {
        this.servicio = servicio;
    }

    /**
     * POST /gastos
     *
     * @Valid dispara Bean Validation sobre el @RequestBody. Si falla, Spring
     * lanza MethodArgumentNotValidException y el ManejadorDeErrores la convierte
     * en un 400 con el detalle por campo. Este metodo no llega a ejecutarse.
     *
     * 201 Created es el codigo correcto al crear un recurso, no 200.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GastoRespuesta crear(@Valid @RequestBody GuardarGastoRequest req) {
        return servicio.crear(req);
    }

    /**
     * GET /gastos?mes=2026-09&categoria=1&pagadoPor=2
     *
     * Los tres parametros son opcionales. Sin `mes`, se asume el mes actual.
     *
     * @DateTimeFormat le dice a Spring como parsear "2026-09" a un YearMonth.
     * Sin eso no sabria interpretar el string.
     *
     * Nota: no hay paginado. Un mes de gastos de dos personas son decenas de
     * filas, no miles. Si algun dia hace falta, se agrega con Pageable -- pero
     * ojo que `join fetch` y paginado no se llevan bien y hay que resolverlo.
     */
    @GetMapping
    public List<GastoRespuesta> listar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) Long pagadoPor) {
        return servicio.listar(mes, categoria, pagadoPor);
    }

    /**
     * PUT /gastos/{id}
     *
     * @PathVariable toma el {id} de la ruta.
     */
    @PutMapping("/{id}")
    public GastoRespuesta actualizar(@PathVariable Long id,
                                     @Valid @RequestBody GuardarGastoRequest req) {
        return servicio.actualizar(id, req);
    }

    /**
     * DELETE /gastos/{id}
     *
     * 204 No Content: se borro y no hay nada que devolver.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
