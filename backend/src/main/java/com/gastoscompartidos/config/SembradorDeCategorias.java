package com.gastoscompartidos.config;

import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.Grupo;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.servicio.CategoriaServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Dos chequeos de arranque para la migracion de categorias globales a
 * categorias por grupo (v1.1, seccion 2.5). Reemplaza al viejo sembrador
 * global: las seis categorias default ya no se siembran una sola vez para
 * toda la base, se siembran POR GRUPO, y el lugar normal para eso es
 * {@code AutenticacionServicio.grupoPropio()} -- esta clase es solo la red de
 * seguridad para lo que ese camino normal no cubre.
 *
 * LO QUE SI CAMBIO, y conviene tenerlo claro (mismo espiritu que la nota
 * original sobre Flyway):
 *
 *  - Esto corre en cada arranque y tiene que verificar por su cuenta que no
 *    haya nada que hacer. Es idempotente porque esta escrito idempotente.
 *  - Y sobre todo: esto siembra, pero NO migra DATOS existentes. Los seis
 *    documentos globales de antes de esta sesion (sin `grupoId`) no se
 *    tocan aca -- quedan huerfanos, sin que nada los lea ni los borre. Para
 *    el UNICO grupo donde eso importa de verdad (el de produccion, con
 *    meses de gastos ya categorizados), la migracion es un comando aparte,
 *    documentado en CLAUDE.md, que hay que correr a mano UNA vez.
 */
@Configuration
public class SembradorDeCategorias {

    private static final Logger log = LoggerFactory.getLogger(SembradorDeCategorias.class);

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
    ApplicationRunner migracionDeCategorias(MongoTemplate mongoTemplate,
                                             GrupoRepositorio grupos,
                                             CategoriaServicio categorias) {
        return args -> {
            dropearIndiceViejoSiExiste(mongoTemplate);
            sembrarGruposSinCategoriasPropias(grupos, categorias);
        };
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

    /**
     * Los grupos nuevos ya salen sembrados desde
     * {@code AutenticacionServicio.grupoPropio()}. Esto es para los que
     * existian ANTES de esa sesion -- en produccion, hoy, cualquier grupo que
     * no sea el que se migre a mano (ver el javadoc de la clase) cae aca.
     *
     * `findAll()` sin paginar: la cantidad de grupos de esta app se cuenta en
     * decenas en el peor caso (dos personas por grupo, registro abierto pero
     * sin trafico real), no en miles. El mismo argumento por el que
     * `LiquidacionRepositorio` suma en Java y no con un pipeline.
     */
    private void sembrarGruposSinCategoriasPropias(GrupoRepositorio grupos, CategoriaServicio categorias) {
        for (Grupo grupo : grupos.findAll()) {
            categorias.sembrarParaGrupo(grupo.getId());
        }
    }
}
