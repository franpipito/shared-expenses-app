package com.gastoscompartidos.config;

import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * Lo que hay que hacerle a la coleccion `categoria` al arrancar para que los
 * datos viejos calcen con el modelo de hoy. Reemplaza a
 * {@code LimpiadorDeIndiceViejo}, que solo sabia dropear un indice.
 *
 * Mongo no tiene esquema ni migraciones (ver "Schema: no hay" en CLAUDE.md), asi
 * que cada cambio de alcance de las categorias dejo algo atras que nadie limpia
 * solo. Son dos pasos, y EL ORDEN IMPORTA: primero los indices, despues los
 * datos, porque el segundo paso escribe documentos que el indice viejo
 * rechazaria.
 *
 * Los dos pasos son idempotentes: en el segundo arranque no encuentran nada que
 * hacer. Es un {@link ApplicationRunner}, o sea que corre una vez por arranque,
 * con el contexto ya armado y antes de que la app atienda requests.
 */
@Configuration
public class MigracionDeCategorias {

    private static final Logger log = LoggerFactory.getLogger(MigracionDeCategorias.class);

    private static final String COLECCION = "categoria";

    /**
     * Los indices unicos que dejaron de estar declarados. `auto-index-creation`
     * crea los que SI estan declarados, pero nada borra uno que ya no lo esta.
     *
     *  - "nombre": el global de antes de la seccion 2.5. OJO CON EL NOMBRE: para
     *    un `@Indexed` de un solo campo sin `name` explicito, Spring Data le
     *    pone el nombre del campo tal cual, y no el "nombre_1" que pondria el
     *    shell de Mongo. Confirmado contra una base real.
     *  - "idx_categoria_grupo_nombre": el de cuando eran por grupo (2.5 a 2.8).
     *    Si quedara vivo, las categorias nuevas -- que ya no llevan `grupo_id`
     *    -- valdrian todas {null, nombre} para ese indice, y el "cafe" de una
     *    segunda persona chocaria contra el de la primera.
     */
    private static final List<String> INDICES_VIEJOS = List.of("nombre", "idx_categoria_grupo_nombre");

    @Bean
    ApplicationRunner migrarCategorias(MongoTemplate mongoTemplate, UsuarioRepositorio usuarios) {
        return args -> {
            dropearIndicesViejos(mongoTemplate);
            repartirLasDelGrupo(mongoTemplate, usuarios);
        };
    }

    private void dropearIndicesViejos(MongoTemplate mongoTemplate) {
        IndexOperations indices = mongoTemplate.indexOps(Categoria.class);
        List<String> existentes = indices.getIndexInfo().stream().map(i -> i.getName()).toList();
        for (String viejo : INDICES_VIEJOS) {
            if (existentes.contains(viejo)) {
                indices.dropIndex(viejo);
                log.info("Indice viejo de categoria eliminado: {}", viejo);
            }
        }
    }

    /**
     * Las categorias que eran de un grupo pasan a ser de cada integrante.
     *
     * La decision (de Franco): cada persona conserva TODAS las que tenia el
     * grupo, y despues borra de su lista las que no usa. No hay forma de hacer
     * algo mas fino, porque nunca se guardo quien creo cada una.
     *
     * Para un grupo de dos, cada categoria termina en dos documentos: el
     * ORIGINAL pasa a la primera persona (conserva el id, que es el que llevan
     * embebido los gastos viejos) y la otra recibe una COPIA con id nuevo. Los
     * gastos viejos de esa segunda persona siguen llevando el id original;
     * `GastoConsultas.actualizarCategoria` los encuentra por nombre.
     *
     * Las copias se escriben ANTES de reclamar el original. Si el proceso muere
     * en el medio, el original sigue sin duenio, el proximo arranque lo vuelve a
     * encontrar, y las copias ya hechas rebotan contra el indice unico en vez
     * de duplicarse.
     *
     * Se leen como `Document` crudo y no como `Categoria` porque el campo
     * `grupo_id` ya no existe en la clase: el modelo de hoy no sabe leerlo.
     *
     * Las que no tienen ni `grupo_id` (las seis globales de antes de la 2.5) y
     * las de un grupo sin integrantes quedan como estan: nadie las consulta.
     */
    private void repartirLasDelGrupo(MongoTemplate mongoTemplate, UsuarioRepositorio usuarios) {
        Query sinDuenio = Query.query(Criteria.where("usuario_id").exists(false)
                .and("grupo_id").exists(true));
        List<Document> viejas = mongoTemplate.find(sinDuenio, Document.class, COLECCION);

        int repartidas = 0;
        for (Document vieja : viejas) {
            List<Usuario> integrantes = usuarios.findByGrupoIdOrderByIdAsc(vieja.getString("grupo_id"));
            if (integrantes.isEmpty()) {
                continue;
            }

            String nombre = vieja.getString("nombre");
            String icono = vieja.getString("icono");

            for (Usuario otro : integrantes.subList(1, integrantes.size())) {
                try {
                    mongoTemplate.insert(new Categoria(otro.getId(), nombre, icono));
                } catch (DuplicateKeyException yaLaTenia) {
                    // Un arranque anterior llego hasta aca. Nada que hacer.
                }
            }

            try {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(vieja.get("_id"))),
                        new Update().set("usuario_id", integrantes.get(0).getId()),
                        COLECCION);
                repartidas++;
            } catch (DuplicateKeyException yaLaTenia) {
                // Esa persona ya tiene otra con el mismo nombre. El original
                // queda sin duenio, que es inofensivo: no aparece en ninguna
                // lista.
                log.warn("Categoria {} sin repartir: su duenio ya tiene una '{}'", vieja.get("_id"), nombre);
            }
        }

        if (repartidas > 0) {
            log.info("Categorias de grupo repartidas entre sus integrantes: {}", repartidas);
        }
    }
}
