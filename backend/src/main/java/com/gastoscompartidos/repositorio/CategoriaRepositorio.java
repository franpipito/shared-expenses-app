package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Categoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de categorias.
 *
 * Sigue siendo una INTERFAZ sin implementacion: Spring Data genera la clase al
 * arrancar y registra el bean. Lo unico que cambio respecto de JPA es de que
 * interfaz se hereda —- MongoRepository en vez de JpaRepository —- y el tipo de
 * la clave, que ahora es String porque el _id de Mongo es un ObjectId.
 *
 * Las consultas derivadas del nombre del metodo funcionan igual, y siguen
 * fallando al arrancar si el nombre no se puede parsear.
 */
public interface CategoriaRepositorio extends MongoRepository<Categoria, String> {

    List<Categoria> findAllByOrderByNombreAsc();

    /** Lo usa el sembrador para no duplicar categorias en cada arranque. */
    Optional<Categoria> findByNombre(String nombre);
}
