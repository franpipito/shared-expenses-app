package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.Categoria;

/**
 * Lo que la API devuelve por una categoria.
 *
 * Podria devolverse la entidad `Categoria` directamente y hoy se veria igual.
 * No lo hacemos por tres motivos:
 *
 *  1. La entidad puede tener campos que no queremos publicar (mira Usuario, que
 *     tiene passwordHash).
 *  2. Con open-in-view=false, serializar una entidad con relaciones LAZY tira
 *     LazyInitializationException al salir de la transaccion.
 *  3. Desacopla: podes renombrar un campo de la entidad sin romper la app
 *     mobile, porque el contrato de la API es este record y no la tabla.
 */
public record CategoriaRespuesta(Long id, String nombre, String icono) {

    /** Fabrica el DTO desde la entidad. Se llama dentro de la transaccion. */
    public static CategoriaRespuesta desde(Categoria categoria) {
        return new CategoriaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getIcono());
    }
}
