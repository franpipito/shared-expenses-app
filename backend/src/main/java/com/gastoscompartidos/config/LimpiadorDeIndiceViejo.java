package com.gastoscompartidos.config;

import com.gastoscompartidos.modelo.Categoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Chequeo de arranque que sobrevive a la migracion de categorias globales a
 * categorias por grupo (v1.1, seccion 2.5), y despues a que se dejaran de
 * sembrar categorias default del todo (seccion 2.7).
 *
 * Reemplaza al viejo {@code SembradorDeCategorias}: ese sembraba las seis
 * categorias default para cada grupo (primero uno global, despues un sweep
 * por grupo). Ninguna de las dos cosas existe mas -- un grupo nace sin
 * ninguna categoria, y la elige en el mini-onboarding o creandola sobre la
 * marcha (ver {@code CategoriaServicio}). Lo unico que esta clase todavia
 * tiene que hacer, y que ningun otro lugar del arranque cubre, es dropear un
 * indice viejo que de otro modo queda vivo para siempre.
 */
@Configuration
public class LimpiadorDeIndiceViejo {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorDeIndiceViejo.class);

    /**
     * Antes de la seccion 2.5, {@code Categoria.nombre} tenia un
     * {@code @Indexed(unique = true)} GLOBAL. El compuesto nuevo
     * ({@code idx_categoria_grupo_nombre}) ya lo crea solo
     * `auto-index-creation`, pero NADA borra automaticamente un indice que
     * dejo de estar declarado -- Spring Data solo garantiza que el que SI
     * esta declarado exista, nunca que el que ya no esta declarado se vaya.
     *
     * Sin este paso, el indice viejo seguiria vivo y el nuevo seria letra
     * muerta: insertar el "cafe" de un SEGUNDO grupo seguiria chocando
     * contra el "cafe" que ya tiene el primero, porque el viejo indice sigue
     * exigiendo que `nombre` sea unico EN TODA LA COLECCION.
     *
     * OJO CON EL NOMBRE: para un `@Indexed` de un solo campo SIN `name`
     * explicito, Spring Data (no Mongo) le pone el nombre del CAMPO tal
     * cual -- "nombre" -- y no el "nombre_1" que pondria el shell de Mongo
     * por su cuenta. Confirmado mirando `db.categoria.getIndexes()` contra
     * una base real: asumir la convencion del shell hacia que este chequeo
     * nunca encontrara el indice, y el drop de mas abajo nunca se ejecutara.
     */
    private static final String INDICE_VIEJO = "nombre";

    @Bean
    ApplicationRunner limpiezaDeIndiceViejo(MongoTemplate mongoTemplate) {
        return args -> dropearIndiceViejoSiExiste(mongoTemplate);
    }

    private void dropearIndiceViejoSiExiste(MongoTemplate mongoTemplate) {
        IndexOperations indices = mongoTemplate.indexOps(Categoria.class);
        boolean existe = indices.getIndexInfo().stream()
                .anyMatch(i -> i.getName().equals(INDICE_VIEJO));
        if (existe) {
            indices.dropIndex(INDICE_VIEJO);
            log.info("Indice viejo de categoria.nombre (global) eliminado: {}", INDICE_VIEJO);
        }
    }
}
