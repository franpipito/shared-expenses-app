package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.CrearCategoriaRequest;
import com.gastoscompartidos.servicio.CategoriaServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @RestController es @Controller + @ResponseBody: lo que devuelve cada metodo
 * NO es el nombre de una vista, es el cuerpo de la respuesta. Jackson lo
 * convierte a JSON solo.
 *
 * @RequestMapping en la clase define el prefijo de ruta comun a todos los
 * metodos.
 *
 * El controlador tiene que quedar fino: recibe, delega, devuelve. Toda decision
 * de negocio vive en el servicio. Si un controlador empieza a tener ifs, algo
 * se filtro de capa.
 */
@RestController
@RequestMapping("/categorias")
public class CategoriaControlador {

    private final CategoriaServicio servicio;

    public CategoriaControlador(CategoriaServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /categorias: las del grupo de quien pregunta, no todas. */
    @GetMapping
    public List<CategoriaRespuesta> listar() {
        return servicio.listar();
    }

    /** POST /categorias */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaRespuesta crear(@Valid @RequestBody CrearCategoriaRequest req) {
        return servicio.crear(req);
    }

    /** DELETE /categorias/{id} */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable String id) {
        servicio.borrar(id);
    }
}
