package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Service marca la clase como un bean de la capa de negocio. Funcionalmente es
 * equivalente a @Component; lo que aporta es intencion: quien lee el codigo sabe
 * de que capa es sin abrir el archivo.
 *
 * Honestidad sobre este servicio en particular: hoy es un pasamanos al
 * repositorio y no agrega nada. Existe para mantener la frontera de capas
 * consistente con GastoServicio, y es donde iria cualquier logica de categorias
 * que aparezca. Si nunca aparece, es una capa de mas -- vale saberlo en vez de
 * poner servicios por reflejo.
 */
@Service
public class CategoriaServicio {

    private final CategoriaRepositorio categorias;

    public CategoriaServicio(CategoriaRepositorio categorias) {
        this.categorias = categorias;
    }

    /**
     * Ya no lleva @Transactional(readOnly = true). Esa anotacion le avisaba a
     * Hibernate que no hiciera dirty checking, y en Mongo no hay dirty checking
     * que evitar: lo que se lee es un objeto comun de Java. Sin sesion que
     * abrir, la anotacion seria puro ruido.
     */
    public List<CategoriaRespuesta> listar() {
        return categorias.findAllByOrderByNombreAsc()
                .stream()
                .map(CategoriaRespuesta::desde)
                .toList();
    }
}
