package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio de categorias.
 *
 * Es una INTERFAZ y no tiene implementacion: Spring Data JPA genera la clase en
 * tiempo de arranque y registra el bean. Por eso tampoco lleva @Repository --
 * esa anotacion no hace falta cuando se extiende JpaRepository, aunque medio
 * internet la ponga igual.
 *
 * JpaRepository<Categoria, Long> = entidad Categoria, con clave primaria Long.
 * De ahi ya vienen gratis findById, findAll, save, delete, count, etc.
 */
public interface CategoriaRepositorio extends JpaRepository<Categoria, Long> {

    /**
     * Consulta derivada del nombre del metodo: Spring Data lo parsea y arma el
     * SQL solo. "findAll" + "OrderBy" + "Nombre" + "Asc" se traduce en
     * SELECT ... FROM categoria ORDER BY nombre ASC.
     *
     * Si el nombre no se puede parsear, la app falla al arrancar y no en runtime.
     */
    List<Categoria> findAllByOrderByNombreAsc();
}
