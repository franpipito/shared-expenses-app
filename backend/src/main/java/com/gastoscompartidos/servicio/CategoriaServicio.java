package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * readOnly = true le avisa a Hibernate que no hace falta el dirty checking
     * (no va a haber UPDATEs), asi que no guarda la foto del estado original de
     * cada entidad. En lecturas grandes ahorra memoria y trabajo.
     */
    @Transactional(readOnly = true)
    public List<CategoriaRespuesta> listar() {
        return categorias.findAllByOrderByNombreAsc()
                .stream()
                .map(CategoriaRespuesta::desde)
                .toList();
    }
}
