package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.servicio.CategoriaServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** GET /categorias */
    @GetMapping
    public List<CategoriaRespuesta> listar() {
        return servicio.listar();
    }
}
